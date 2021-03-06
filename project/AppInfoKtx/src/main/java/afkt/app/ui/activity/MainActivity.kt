package afkt.app.ui.activity

import afkt.app.R
import afkt.app.module.ActionEnum
import afkt.app.module.TypeEnum
import afkt.app.module.event.*
import afkt.app.ui.fragment.AppListFragment
import afkt.app.ui.fragment.InfoFragment
import afkt.app.ui.fragment.ScanSDCardFragment
import afkt.app.ui.fragment.SettingFragment
import afkt.app.utils.EventBusUtils
import afkt.app.utils.SearchUtils
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import dev.utils.app.ClickUtils
import dev.utils.app.ResourceUtils
import dev.utils.app.ViewUtils
import dev.utils.app.toast.ToastTintUtils
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : AppCompatActivity(), View.OnClickListener {

    // = View =

    @JvmField
    @BindView(R.id.vid_am_drawer_layout)
    var vid_am_drawer_layout: DrawerLayout? = null

    @JvmField
    @BindView(R.id.vid_am_toolbar)
    var vid_am_toolbar: Toolbar? = null

    @JvmField
    @BindView(R.id.vid_am_linear)
    var vid_am_linear: LinearLayout? = null

    @JvmField
    @BindView(R.id.vid_am_top_btn)
    var vid_am_top_btn: FloatingActionButton? = null

    @JvmField
    @BindView(R.id.vid_am_nav_view)
    var vid_am_nav_view: NavigationView? = null

    var searchView: SearchView? = null

    // = Object =

    var mFragments: HashMap<TypeEnum, Fragment> = HashMap()
    var mFragmentManager: FragmentManager? = null
    var mFragmentType = TypeEnum.NONE // 判断当前 Fragment 类型
    var DISPLAY_FRAGMENT_TYPE = TypeEnum.APP_USER // 默认显示 Fragment Type

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ButterKnife.bind(this)
        EventBusUtils.register(this)

        init()
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBusUtils.unregister(this)
    }

    fun init() {
        initFragments()
        initListeners()
        // 设置标题
        vid_am_nav_view?.setCheckedItem(getNavItemId())
        vid_am_toolbar?.setTitle(DISPLAY_FRAGMENT_TYPE.titleId)
        // 设置侧边栏
        setSupportActionBar(vid_am_toolbar)
        // 设置切换动画事件等
        val toggle = ActionBarDrawerToggle(
            MainActivity@ this, vid_am_drawer_layout, vid_am_toolbar,
            R.string.str_navigation_drawer_open,
            R.string.str_navigation_drawer_close
        )
        vid_am_drawer_layout?.addDrawerListener(toggle)
        toggle.syncState()
        // 显示指定 Fragment Type
        toggleFragment(DISPLAY_FRAGMENT_TYPE)
    }

    fun initFragments() {
        mFragments.putAll(
            mapOf(
                // 用户应用信息
                TypeEnum.APP_USER to AppListFragment.get(TypeEnum.APP_USER),
                // 系统应用信息
                TypeEnum.APP_SYSTEM to AppListFragment.get(TypeEnum.APP_SYSTEM),
                // 设备信息
                TypeEnum.DEVICE_INFO to InfoFragment.get(TypeEnum.DEVICE_INFO),
                // 屏幕信息
                TypeEnum.SCREEN_INFO to InfoFragment.get(TypeEnum.SCREEN_INFO),
                // 扫描 SDK
                TypeEnum.QUERY_APK to ScanSDCardFragment(),
                // 设置
                TypeEnum.SETTING to SettingFragment()
            )
        )

        mFragmentManager = supportFragmentManager
        val transaction: FragmentTransaction? = mFragmentManager?.beginTransaction()
        for (item in mFragments.values) {
            transaction?.add(R.id.vid_am_linear, item, item.toString())
                ?.hide(item)
        }
        transaction?.commit()
    }

    /**
     * 切换 Fragment
     * @param type Fragment Type
     */
    private fun toggleFragment(type: TypeEnum) {
        if (type != mFragmentType) {
            val transaction: FragmentTransaction? = mFragmentManager?.beginTransaction()
            // 隐藏当前显示的 Fragment
            mFragments[mFragmentType]?.let { transaction?.hide(it) }
            // 准备显示的 Fragment
            mFragments[type]?.let { transaction?.show(it)?.commit() }
            // 是否 NONE 类型
            val noneType = mFragmentType == TypeEnum.NONE
            // 保存 type
            mFragmentType = type

            // 回到顶部按钮控制
            when (type) {
                TypeEnum.APP_USER, TypeEnum.APP_SYSTEM, TypeEnum.QUERY_APK -> {
                    ViewUtils.setVisibility(true, vid_am_top_btn)
                }
                else -> ViewUtils.setVisibility(false, vid_am_top_btn)
            }
            // 通知系统更新菜单
            invalidateOptionsMenu()
            // 发送 Fragment 切换通知事件
            EventBusUtils.post(FragmentEvent(type))
            // 设置标题
            vid_am_toolbar?.setTitle(type.titleId)
            vid_am_nav_view?.setCheckedItem(getNavItemId())
            // 失去焦点 ( 解决存在键盘, 点击侧边导航栏切换, 输入法键盘还存在 )
            searchView?.let { it.clearFocus() }
            // 发送粘性事件 - 只会发送一次, 刚进入为 NONE, 解决 Fragment show 之前消息已经发送无法接收
            if (noneType) EventBusUtils.postSticky(FragmentEvent(type))
        }
    }

    fun initListeners() {
        // 设置 NavigationView Item 点击事件
        vid_am_nav_view?.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_user_apps -> toggleFragment(TypeEnum.APP_USER)
                R.id.nav_system_apps -> toggleFragment(TypeEnum.APP_SYSTEM)
                R.id.nav_phone_info -> toggleFragment(TypeEnum.DEVICE_INFO)
                R.id.nav_screen_info -> toggleFragment(TypeEnum.SCREEN_INFO)
                R.id.nav_query_apk -> toggleFragment(TypeEnum.QUERY_APK)
                R.id.nav_setting -> toggleFragment(TypeEnum.SETTING)
            }
            vid_am_drawer_layout?.closeDrawer(GravityCompat.START)
            true
        }
    }

    fun getNavItemId(): Int {
        when (DISPLAY_FRAGMENT_TYPE) {
            TypeEnum.APP_USER -> return R.id.nav_user_apps
            TypeEnum.APP_SYSTEM -> return R.id.nav_system_apps
            TypeEnum.DEVICE_INFO -> return R.id.nav_phone_info
            TypeEnum.SCREEN_INFO -> return R.id.nav_screen_info
            TypeEnum.QUERY_APK -> return R.id.nav_query_apk
            TypeEnum.SETTING -> R.id.nav_setting
        }
        return R.id.nav_user_apps
    }

    // ===========
    // = OnClick =
    // ===========

    @OnClick(R.id.vid_am_top_btn)
    override fun onClick(v: View) {
        EventBusUtils.post(TopEvent(mFragmentType))
    }

    // ========
    // = Back =
    // ========

    override fun onBackPressed() {
        // 如果显示了侧边栏, 则关闭
        if (vid_am_drawer_layout!!.isDrawerOpen(GravityCompat.START)) {
            vid_am_drawer_layout?.closeDrawer(GravityCompat.START)
        } else {
            // 判断是否显示, 是的话则关闭
            if (searchView != null && !searchView!!.isIconified) {
                if (!searchView?.query.toString().isEmpty()) {
                    searchView?.setQuery("", false)
                }
                searchView?.isIconified = true
                return
            }
            // 判断是否默认 Fragment Type 不是的话, 则进行切换
            if (mFragmentType != DISPLAY_FRAGMENT_TYPE) {
                toggleFragment(DISPLAY_FRAGMENT_TYPE)
                return
            }
            if (ClickUtils.isFastDoubleClick(R.string.str_click_return)) {
                super.onBackPressed()
            } else {
                ToastTintUtils.info(ResourceUtils.getString(R.string.str_click_return))
                return
            }
        }
    }

    // ========
    // = Menu =
    // ========

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.bar_menu_apps, menu)
        return super.onCreateOptionsMenu(menu)
    }

    // 准备显示Menu
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        when (mFragmentType) {
            TypeEnum.DEVICE_INFO, TypeEnum.SCREEN_INFO -> menuInflater.inflate(
                R.menu.bar_menu_device, menu
            )
            TypeEnum.APP_USER, TypeEnum.APP_SYSTEM, TypeEnum.QUERY_APK -> {
                menuInflater.inflate(R.menu.bar_menu_apps, menu)
                // 初始化搜索操作
                initSearchOperate(menu)
            }
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.bma_refresh -> EventBusUtils.post(RefreshEvent(mFragmentType))
            R.id.bmd_export_item -> EventBusUtils.post(ExportEvent(mFragmentType))
        }
        return true
    }

    /**
     * 初始化搜索操作
     * @param menu
     */
    private fun initSearchOperate(menu: Menu) {
        val searchItem = menu.findItem(R.id.bma_search)
        // 初始化搜索 View
        searchView = searchItem.actionView as SearchView
        // 默认提示
        searchView!!.queryHint = getString(R.string.str_input_packname_aname_query)
        // 搜索框展开时后面 X 按钮点击事件
        searchView!!.setOnCloseListener(object : SearchView.OnCloseListener {
            override fun onClose(): Boolean {
                SearchUtils.removeSearchTask()
                // 发送搜索合并通知事件
                EventBusUtils.post(SearchEvent(mFragmentType, ActionEnum.COLLAPSE))
                return false
            }
        })
        // 搜索图标按钮 ( 打开搜索框的按钮 ) 点击事件
        searchView!!.setOnSearchClickListener {
            SearchUtils.removeSearchTask()
            // 发送搜索展开通知事件
            EventBusUtils.post(SearchEvent(mFragmentType, ActionEnum.EXPAND))
        }
        // 搜索文本监听
        searchView!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            // 当点击搜索按钮时触发该方法
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            // 当搜索内容改变时触发该方法
            override fun onQueryTextChange(newText: String?): Boolean {
                SearchUtils.startSearchTask()
                return false
            }
        })
    }

    // ============
    // = 事件相关 =
    // ============

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: StartSearchEvent) {
        var search = SearchEvent(mFragmentType, ActionEnum.CONTENT)
        search.content = searchView?.query.toString()
        EventBusUtils.post(search)
    }
}