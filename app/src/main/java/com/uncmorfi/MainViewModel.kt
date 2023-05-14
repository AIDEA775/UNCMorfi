package com.uncmorfi

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.uncmorfi.data.network.clientBeta
import com.uncmorfi.data.network.models.ReservationResponse
import com.uncmorfi.data.persistence.AppDatabase
import com.uncmorfi.data.persistence.entities.DayMenu
import com.uncmorfi.data.persistence.entities.Reservation
import com.uncmorfi.data.persistence.entities.Serving
import com.uncmorfi.data.persistence.entities.User
import com.uncmorfi.data.repository.RepoMenu
import com.uncmorfi.data.repository.RepoServings
import com.uncmorfi.data.repository.RepoUser
import com.uncmorfi.shared.*
import com.uncmorfi.shared.ReserveStatus.*
import com.uncmorfi.shared.StatusCode.*
import kotlinx.coroutines.*
import org.json.JSONException
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.util.*
import kotlin.coroutines.coroutineContext

class MainViewModel(val context: Application) : AndroidViewModel(context) {
    private val db: AppDatabase = AppDatabase(context)
    private val repoMenu = RepoMenu(context)
    private val repoUser = RepoUser(context)
    private val repoServings = RepoServings(context)

    val status: MutableLiveData<StatusCode> = MutableLiveData()
    val reservation: MutableLiveData<ReserveStatus> = MutableLiveData()
    val reserveTry: MutableLiveData<Int> = MutableLiveData()
    var reserveJob: Job? = null

    init {
        status.value = BUSY
        reservation.value = NOCACHED
    }

    /*
     * Balance stuff
     */

    fun getAllUsers(): LiveData<List<User>> = repoUser.getAll()

    fun getUser(card: String): LiveData<User?> = repoUser.getBy(card)

    fun updateCards(card: String) = launchIO {
        if (!context.isOnline()) {
            status.postValue(NO_ONLINE)
            return@launchIO
        }
        status.postValue(UPDATING)
//        delay(4_000)
        val updates = repoUser.fetch(card)
        status.postValue(if (updates > 0) UPDATE_SUCCESS else USER_INSERTED)
    }

    fun updateUserName(user: User) = launchIO {
        repoUser.fullUpdate(user)
        status.postValue(UPDATE_SUCCESS)
    }

    fun deleteUser(user: User) = launchIO {
        repoUser.delete(user)
        status.postValue(USER_DELETED)
    }

    /*
     * Menu stuff
     */
    fun getMenu(): LiveData<List<DayMenu>> = repoMenu.getAll()

    fun refreshMenu() = launchIO {
        if (needAutoUpdateMenu()) {
            forceRefreshMenu()
        }
    }

    fun forceRefreshMenu() = launchIO {
        Log.d("ViewModel", "Force update menu")
        if (!context.isOnline()) {
            status.postValue(NO_ONLINE)
            return@launchIO
        }
        status.postValue(UPDATING)
        val inserts = repoMenu.update()

        status.postValue(when {
            inserts.isEmpty() -> UPDATE_ERROR
            inserts.all { it == -1L } -> ALREADY_UPDATED
            else -> UPDATE_SUCCESS
        })
    }

    fun clearMenu() = launchIO{
        repoMenu.clear()
        forceRefreshMenu()
    }

    private suspend fun needAutoUpdateMenu(): Boolean {
        val now = LocalDate.now()
        val last = repoMenu.last() ?: return true
        return last.date.isBefore(now)
    }

    /*
     * Serving stuff
     */
    fun getServings(): LiveData<List<Serving>> = repoServings.getToday()

    fun updateServings() = launchIO {
        Log.d("ViewModel", "Update serving")
        if (!context.isOnline()) {
            status.postValue(NO_ONLINE)
            return@launchIO
        }
        status.postValue(UPDATING)
        val inserts = repoServings.update()

        status.postValue(when {
            inserts.isEmpty() -> EMPTY_UPDATE
            inserts.all { it == -1L } -> ALREADY_UPDATED
            else -> UPDATE_SUCCESS
        })
    }

    /*
     * Reservation stuff
     */
    fun reserveIsCached(user: User) {
        mainDispatch {
            val reserve = getReserve(user.card)
            if (reserve != null) {
                reservation.value = CACHED
            } else {
                reservation.value = NOCACHED
            }
        }
    }

    fun reserveLogin(user: User, captcha: String) {
        mainDispatch {
            val reserve = clientBeta.getLogin(user.card)
            reserve.captchaText = captcha

            val result = ioDispatch { clientBeta.doLogin(reserve) }
            result?.let {
                insertReservation(result)
                reservation.value = CACHED
            }
        }
    }

    fun reserveConsult(user: User) {
        mainDispatch {
            val reserve = getReserve(user.card)
            if (reserve != null) {
                if (context.isOnline()) {
                    reservation.value = CONSULTING
                    val result = ioDispatch { clientBeta.status(reserve) }
                    result?.updateReservation(reserve)
                } else {
                    status.value = NO_ONLINE
                }
            } else {
                reservation.value = REDOLOGIN
            }
        }
    }

    fun reserve(user: User) {
        mainDispatch {
            val reserve = getReserve(user.card)
            if (reserve != null) {
                if (context.isOnline()) {
                    reservation.value = RESERVING
                    val result = ioDispatch { clientBeta.reserve(reserve) }
                    result?.updateReservation(reserve)
                } else {
                    status.value = NO_ONLINE
                }
            } else {
                reservation.value = REDOLOGIN
            }
        }
    }

    fun reserveLoop(user: User) {
        mainDispatch {
            val reserve = getReserve(user.card)
            if (reserve == null) {
                reservation.value = REDOLOGIN
                return@mainDispatch
            }
            reserveJob?.cancel()
            reserveJob = mainDispatch {
                var intent = 0

                do {
                    intent += 1
                    reserveTry.value = intent
                    val result = ioDispatch { clientBeta.reserve(reserve) }
                    val status = result?.updateReservation(reserve)
                    delay(1500)
                } while (status != RESERVED && status != REDOLOGIN)

                reserveTry.value = 0
            }
        }
    }

    fun reserveStop() {
        reserveJob?.cancel()
        reserveJob = null
        reserveTry.value = 0
        status.value = BUSY
    }

    fun reserveLogout(user: User) {
        mainDispatch {
            db.reserveDao().delete(user.card)
            reservation.value = NOCACHED
        }
    }

    private suspend fun ReservationResponse.updateReservation(reserve: Reservation): ReserveStatus {
        this.path?.let { reserve.path = it }
        this.token?.let { reserve.token = it }

        val status = ReserveStatus.valueOf(this.reservationResult.toUpperCase())
        reservation.value = status

        if (status == REDOLOGIN) {
            db.reserveDao().delete(reserve.code)
        } else {
            insertReservation(reserve)
        }
        return status
    }

    private suspend fun insertReservation(reserve: Reservation) {
        // Guardar cookie con el codigo de la tarjeta a la que pertenece
        reserve.cookies?.map { c -> c.code = reserve.code }
        db.reserveDao().insert(reserve)
    }

    private suspend fun getReserve(code: String): Reservation? {
        val reserve = db.reserveDao().getReservation(code)
        reserve?.cookies = db.reserveDao().getCookies(code)
        return reserve
    }

    private fun mainDispatch(f: suspend (CoroutineScope) -> Unit): Job {
        return viewModelScope.launch(Dispatchers.Main, block = f)
    }

    private fun launchIO(f: suspend (CoroutineScope) -> Unit) {
        try {
            viewModelScope.launch(Dispatchers.IO, block = f)
        } catch (e: HttpException) {
            e.printStackTrace()
            status.value = CONNECT_ERROR
        } catch (e: IOException) {
            e.printStackTrace()
            status.value = CONNECT_ERROR
        } catch (e: JSONException) {
            e.printStackTrace()
            status.value = INTERNAL_ERROR
        } catch (e: Exception) {
            e.printStackTrace()
            status.value = INTERNAL_ERROR
        }
    }

    private suspend fun <T> ioDispatch(f: suspend (CoroutineScope) -> T): T? {
        var result: T? = null
        try {
            result = withContext(coroutineContext + Dispatchers.IO, block = f)
        } catch (e: HttpException) {
            e.printStackTrace()
            status.value = CONNECT_ERROR
        } catch (e: IOException) {
            e.printStackTrace()
            status.value = CONNECT_ERROR
        } catch (e: JSONException) {
            e.printStackTrace()
            status.value = INTERNAL_ERROR
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            status.value = INTERNAL_ERROR
        }
        return result
    }

}