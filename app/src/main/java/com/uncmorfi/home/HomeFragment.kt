package com.uncmorfi.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.uncmorfi.R
import com.uncmorfi.balance.dialogs.UserOptionsDialog
import com.uncmorfi.models.User
import com.uncmorfi.shared.ReserveStatus.NOCACHED
import com.uncmorfi.shared.SnackType.FINISH
import com.uncmorfi.shared.SnackType.LOADING
import com.uncmorfi.shared.StatusCode.*
import com.uncmorfi.shared.compareToToday
import com.uncmorfi.shared.getUser
import com.uncmorfi.shared.snack
import com.uncmorfi.viewmodel.MainViewModel
import kotlinx.android.synthetic.main.fragment_home.*

class HomeFragment : Fragment() {
    private lateinit var mViewModel: MainViewModel
    private lateinit var mUser: User
    private lateinit var mRootView: View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mRootView = view
        mViewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)

        /*
         * Menú
         */
        mViewModel.getMenu().observe(this, Observer { menuList ->
            val today = menuList.firstOrNull { it.date.compareToToday() == 0 }

            if (today != null) {
                homeMenu.setDayMenu(today)
                homeMenuTitle.visibility = VISIBLE
                homeMenuContainer.visibility = VISIBLE
            } else {
                homeMenuTitle.visibility = GONE
                homeMenuContainer.visibility = GONE
            }
        })

        /*
         * Tarjetas
         */
        mViewModel.allUsers().observe(this, Observer {
            if (it.isNotEmpty()) {
                mUser = it.first()
                homeCard.setUser(mUser)
                homeCardTitle.visibility = VISIBLE
                homeCardContainer.visibility = VISIBLE
            } else {
                homeCardTitle.visibility = GONE
                homeCardContainer.visibility = GONE
            }
        })
        homeCardContainer.setOnClickListener {
            UserOptionsDialog
                    .newInstance(this, USER_OPTIONS_CODE, mUser)
                    .show(fragmentManager!!, "UserOptionsDialog")
        }
        homeCardContainer.setOnLongClickListener {
            mUser.isLoading = true
            homeCard.setUser(mUser)
            mViewModel.downloadUsers(mUser)
            true
        }
        mViewModel.userStatus.observe(this, Observer {
            when (it) {
                BUSY -> {}
                UPDATE_ERROR -> mRootView.snack(R.string.snack_new_user_not_found, FINISH)
                DELETED -> mRootView.snack(R.string.snack_delete_user_done, FINISH)
                else -> mRootView.snack(it)
            }
        })

        /*
         * Medidor
         */
        mViewModel.getServings().observe(this, Observer {
            if (it.isNotEmpty()) {
                servingsPieChart.set(it)
            }
        })
        servingsPieChart.setTouchEnabled(false)
        homeServingsContainer.setOnClickListener {
            mRootView.snack(R.string.snack_updating, LOADING)
            mViewModel.updateServings()
        }
        mViewModel.servingStatus.observe(this, Observer {
            when (it) {
                BUSY -> {}
                else -> mRootView.snack(it)
            }
        })

        /*
         * Reservas
         */
        mViewModel.reserveStatus.observe(this, Observer {
            mRootView.snack(it)
        })

        mViewModel.reserveTry.observe(this, Observer {
            mRootView.snack(getString(R.string.snack_loop).format(it), LOADING)
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            USER_OPTIONS_CODE -> {
                val user = data.getUser()
                user.isLoading = true
                homeCard.setUser(user)
                mViewModel.downloadUsers(user)
            }
            else -> {
                super.onActivityResult(requestCode, resultCode, data)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.app_name)
    }

    override fun onStop() {
        super.onStop()
        mViewModel.menuStatus.value = BUSY
        mViewModel.userStatus.value = BUSY
        mViewModel.servingStatus.value = BUSY
        mViewModel.reserveStatus.value = NOCACHED
    }
    companion object {
        private const val USER_OPTIONS_CODE = 1
    }
}
