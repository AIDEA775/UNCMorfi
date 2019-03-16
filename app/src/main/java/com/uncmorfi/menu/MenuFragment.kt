package com.uncmorfi.menu

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import com.uncmorfi.R
import com.uncmorfi.helpers.*
import kotlinx.android.synthetic.main.fragment_menu.*
import java.util.*

/**
 * Menú de la semana.
 * Administra la UI y el guardado persistente del menú.
 * Usa a [RefreshMenuTask] para actualizar el menú.
 */
class MenuFragment : Fragment() {
    private lateinit var mRootView: View
    private lateinit var mMenuAdapter: MenuAdapter
    private lateinit var mApplicationContext: Context

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        mRootView = inflater.inflate(R.layout.fragment_menu, container, false)
        mApplicationContext = requireActivity().applicationContext
        return mRootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        menuSwipeRefresh.init { refreshMenu() }
        initRecyclerAndAdapter()
        initMenu()

        if (needAutoRefreshMenu())
            refreshMenu()
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.navigation_menu)
    }

    override fun onStop() {
        super.onStop()
        menuSwipeRefresh.isRefreshing = false
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_update -> { refreshMenu(); true }
            R.id.menu_browser -> requireActivity().startBrowser(URL)
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initRecyclerAndAdapter() {
        menuRecyclerView.setHasFixedSize(true)
        val layoutManager = LinearLayoutManager(context)
        menuRecyclerView.layoutManager = layoutManager
    }

    private fun initMenu() {
        mMenuAdapter = MenuAdapter(mApplicationContext, getSavedMenu(),
                { onClick(it) },
                { onLongClick(it) })
        menuRecyclerView.adapter = mMenuAdapter
    }

    private fun refreshMenu() {
        if (mApplicationContext.isOnline()) {
            menuSwipeRefresh.isRefreshing = true
            RefreshMenuTask { onDayMenuDownloaded(it) } .execute()
        } else {
            menuSwipeRefresh.isRefreshing = false
            mRootView.snack(context, R.string.no_connection, SnackType.ERROR)
        }
    }

    private fun onClick(dayMenu: DayMenu) {
        requireActivity().shareText(
                getString(R.string.menu_share_subject),
                dayMenu.toString() + "\n\n" + getString(R.string.menu_share_banner),
                getString(R.string.menu_share_subject))
    }

    private fun onLongClick(dayMenu: DayMenu) {
        val clipboard = mApplicationContext.
                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Food", dayMenu.toString())
        clipboard.primaryClip = clip
        mRootView.snack(context, R.string.menu_copy_msg, SnackType.FINISH)
    }

    private fun onDayMenuDownloaded(download: String?) {
        val menu = download.toDayMenuList()

        if (activity != null && isAdded) {
            menuSwipeRefresh.isRefreshing = false

            if (!menu.isEmpty()) {
                mMenuAdapter.updateMenu(menu)
                mRootView.snack(context, R.string.update_success, SnackType.FINISH)
                mApplicationContext.saveToStorage(MENU_FILE, download)
            } else {
                mRootView.snack(context, R.string.update_fail, SnackType.ERROR)
            }
        }
    }

    private fun getSavedMenu() : List<DayMenu> {
        return mApplicationContext.readStringFromStorage(MENU_FILE).toDayMenuList()
    }

    private fun needAutoRefreshMenu(): Boolean {
        val now = Calendar.getInstance()
        now.time = Date()
        val nowWeek = now.get(Calendar.WEEK_OF_YEAR)
        val nowYear = now.get(Calendar.YEAR)

        val menu = Calendar.getInstance()
        menu.time = getSavedMenu().firstOrNull()?.date ?: Date(0) // Date(0) es 1970
        val menuWeek = menu.get(Calendar.WEEK_OF_YEAR)
        val menuYear = now.get(Calendar.YEAR)

        return menuYear < nowYear || menuWeek < nowWeek
    }

    companion object {
        private const val URL = "https://www.unc.edu.ar/vida-estudiantil/men%C3%BA-de-la-semana"
        const val MENU_FILE = "menu.json"
    }

}