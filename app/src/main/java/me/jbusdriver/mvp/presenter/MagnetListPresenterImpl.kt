package me.jbusdriver.mvp.presenter

import io.reactivex.Flowable
import io.reactivex.rxkotlin.addTo
import me.jbusdriver.common.CacheLoader
import me.jbusdriver.common.GSON
import me.jbusdriver.common.SchedulersCompat
import me.jbusdriver.common.fromJson
import me.jbusdriver.mvp.MagnetListContract
import me.jbusdriver.mvp.bean.Magnet
import me.jbusdriver.mvp.bean.PageInfo
import me.jbusdriver.mvp.bean.ResultPageBean
import me.jbusdriver.mvp.model.BaseModel
import me.jbusdriver.ui.data.magnet.MagnetLoaders
import org.jsoup.nodes.Document

class MagnetListPresenterImpl(private val magnetLoaderKey: String, private val keyword: String) : AbstractRefreshLoadMorePresenterImpl<MagnetListContract.MagnetListView, Magnet>(), MagnetListContract.MagnetListPresenter {

    private val loader by lazy { MagnetLoaders[magnetLoaderKey] ?: error("not matched magnet loader") }

    override val model: BaseModel<Int, Document>
        get() = error("not call model")

    override fun stringMap(page: PageInfo, str: Document): List<Magnet> = error("not call stringMap")


    private val cacheKey: String
        get() = "${magnetLoaderKey}_${keyword}_${pageInfo.activePage}"

    override fun loadData4Page(page: Int) {
        val curPage = PageInfo(page, page)
        //page 1
        val cache = Flowable.concat(CacheLoader.justLru(cacheKey), CacheLoader.justDisk(cacheKey)).firstElement().map { GSON.fromJson<List<Magnet>>(it) }.toFlowable()
        val loaderFormNet = Flowable.fromCallable {
            loader.loadMagnets(keyword, page)
        }.doOnNext {
            if (it.isNotEmpty() && page <= 1) {
                CacheLoader.cacheDisk(cacheKey to it)
                CacheLoader.cacheLru(cacheKey to it)
            }
        }
        Flowable.concat(cache, loaderFormNet).firstOrError().toFlowable()
                .map { ResultPageBean(curPage, it) }
                .compose(SchedulersCompat.io())
                .subscribeWith(ListDefaultSubscriber(curPage))
                .addTo(rxManager)
    }

    override fun lazyLoad() {
        onFirstLoad()
    }

    override fun hasLoadNext(): Boolean = loader.hasNexPage.also {
        if (!it){
            lastPage = pageInfo.activePage
        }
    }

    override fun onRefresh() {
        (0..Math.max(pageInfo.activePage, pageInfo.nextPage)).onEach {
            CacheLoader.lru.remove(cacheKey)
            CacheLoader.acache.remove(cacheKey)
        }
        super.onRefresh()
    }
}