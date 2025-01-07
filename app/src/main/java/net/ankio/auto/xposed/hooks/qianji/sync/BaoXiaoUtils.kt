/*
 * Copyright (C) 2024 ankio(ankio@ankio.net)
 * Licensed under the Apache License, Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-3.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package net.ankio.auto.xposed.hooks.qianji.sync

import com.google.gson.Gson
import de.robv.android.xposed.XposedHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ankio.auto.xposed.core.hook.Hooker
import net.ankio.auto.xposed.core.utils.AppRuntime
import net.ankio.auto.xposed.core.utils.MD5HashTable
import net.ankio.auto.xposed.core.utils.MessageUtils
import net.ankio.auto.xposed.hooks.qianji.models.Bill
import org.ezbook.server.constant.Setting
import org.ezbook.server.db.model.BillInfoModel
import org.ezbook.server.db.model.BookBillModel
import org.ezbook.server.db.model.SettingModel
import java.lang.reflect.Proxy
import java.util.Calendar
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class BaoXiaoUtils {

    val baoXiaoImpl by lazy {
        Hooker.loader("com.mutangtech.qianji.bill.baoxiao.BxPresenterImpl")
    }


    private suspend fun getBaoXiaoList(all: Boolean = false): List<*> =
        suspendCoroutine { continuation ->
            var resumed = false
            val constructor = baoXiaoImpl.constructors.first()!!
            // public BxPresenterImpl(t8.b bVar) {
            val param1Clazz = constructor.parameterTypes.first()!!
            val param1Object = Proxy.newProxyInstance(
                AppRuntime.classLoader,
                arrayOf(param1Clazz)
            ) { proxy, method, args ->
                if (method.name == "onGetList") {
                    if (!resumed) {
                        resumed = true
                        val billList = args[0]
                        continuation.resume(billList as List<*>)
                    }
                }
            }
            // public void refresh(t8.c cVar, BookFilter bookFilter, KeywordFilter keywordFilter) {
            val refreshMethod = baoXiaoImpl.declaredMethods.find { it.name == "refresh" }!!


            val clazzEnum = refreshMethod.parameters[0].type

            val enumValue =
                clazzEnum?.declaredFields?.firstOrNull { it.name == if (all) "ALL" else "NOT" }!!
                    .get(null)

            //BookFilter
            val bookFilter = XposedHelpers.newInstance(refreshMethod.parameters[1].type)
            //KeywordFilter
            val keywordFilter = XposedHelpers.newInstance(refreshMethod.parameters[2].type, "")


            XposedHelpers.callMethod(
                XposedHelpers.newInstance(baoXiaoImpl, param1Object),
                "refresh",
                enumValue,
                bookFilter,
                keywordFilter
            )

        }


    suspend fun syncBaoXiao() = withContext(Dispatchers.IO) {
        // 报销账单
        val bxList =
            withContext(Dispatchers.Main) {
                getBaoXiaoList()
            }

        /**
         * {
         *     "_id": 11002,
         *     "assetid": 1613058959055,
         *     "billid": 1718199912441166031,
         *     "bookId": -1,
         *     "category": {
         *         "bookId": -1,
         *         "editable": 0,
         *         "icon": "http://qianjires.xxoojoke.com/cateic_other.png",
         *         "id": 6691047,
         *         "level": 1,
         *         "name": "其它",
         *         "parentId": -1,
         *         "sort": 10,
         *         "type": 0,
         *         "userId": "200104405e109647c18e9"
         *     },
         *     "categoryId": 6691047,
         *     "createtimeInSec": 1718199912,
         *     "descinfo": "支付宝-余额宝",
         *     "fromact": "支付宝-余额宝",
         *     "fromid": -1,
         *     "importPackId": 0,
         *     "money": 0.01,
         *     "paytype": 0,
         *     "platform": 0,
         *     "remark": "长城基金管理有限公司 -222222",
         *     "status": 1,
         *     "targetid": -1,
         *     "timeInSec": 1715020286,
         *     "type": 5,
         *     "updateTimeInSec": 0,
         *     "userid": "200104405e109647c18e9"
         * }*/

        /**
         * {
         *     "_id": 11002,
         *     "assetid": 1613058959055,
         *     "billid": 1718199912441166031,
         *     "bookId": -1,
         *     "category": {
         *         "bookId": -1,
         *         "editable": 0,
         *         "icon": "http://qianjires.xxoojoke.com/cateic_other.png",
         *         "id": 6691047,
         *         "level": 1,
         *         "name": "其它",
         *         "parentId": -1,
         *         "sort": 10,
         *         "type": 0,
         *         "userId": "200104405e109647c18e9"
         *     },
         *     "categoryId": 6691047,
         *     "createtimeInSec": 1718199912,
         *     "descinfo": "支付宝-余额宝",
         *     "fromact": "支付宝-余额宝",
         *     "fromid": -1,
         *     "importPackId": 0,
         *     "money": 0.01,
         *     "paytype": 0,
         *     "platform": 0,
         *     "remark": "长城基金管理有限公司 -222222",
         *     "status": 1,
         *     "targetid": -1,
         *     "timeInSec": 1715020286,
         *     "type": 5,
         *     "updateTimeInSec": 0,
         *     "userid": "200104405e109647c18e9"
         * }*/

        val bills = convert2Bill(bxList)
        val sync = Gson().toJson(bills)
        val md5 = MD5HashTable.md5(sync)
        val server = SettingModel.get(Setting.HASH_BILL, "")
        if (server == md5 && !AppRuntime.debug) {
            AppRuntime.log("No need to sync BaoXiao, server md5:${server} local md5:${md5}")
            return@withContext
        }
        AppRuntime.logD("Sync BaoXiao:$sync")
        BookBillModel.put(bills, md5)
        withContext(Dispatchers.Main) {
            MessageUtils.toast("已同步报销账单到自动记账")
        }
    }

    suspend fun doBaoXiao(billModel: BillInfoModel) = withContext(Dispatchers.Main) {

        val list = billModel.extendData.split(", ")

        val billList =
            withContext(Dispatchers.Main) {
                getBaoXiaoList(true)
            }

        val selectBills =
            billList.filter {
                val billId = XposedHelpers.getObjectField(it, "billid") as Long
                // 判断billId是否在list中
                list.contains(billId.toString())
            }

        if (selectBills.isEmpty()) {
            throw RuntimeException("没有找到需要报销的账单")
        }

        val constructor = baoXiaoImpl.constructors.first()!!
        // public BxPresenterImpl(t8.b bVar) {
        val param1Clazz = constructor.parameterTypes.first()!!
        val param1Object = Proxy.newProxyInstance(
            AppRuntime.classLoader,
            arrayOf(param1Clazz)
        ) { _, _, _ ->

        }
        val baoXiaoInstance = XposedHelpers.newInstance(baoXiaoImpl, param1Object)

        //    public void doBaoXiao(
        //    java.util.Set<? extends com.mutangtech.qianji.data.model.Bill> r36,
        //    com.mutangtech.qianji.data.model.AssetAccount r37,
        //    double r38,
        //    java.util.Calendar r40,
        //    com.mutangtech.qianji.data.model.CurrencyExtra r41,
        //    java.lang.String r42,
        //    java.util.List<java.lang.String> r43,
        //    java.util.List<? extends com.mutangtech.qianji.data.model.Tag> r44) {


        // java.util.Set<? extends com.mutangtech.qianji.data.model.Bill> r36,
        val set = HashSet<Any>(selectBills)

        // com.mutangtech.qianji.data.model.AssetAccount r37,
        val asset =
            AssetsUtils().getAssetByName(billModel.accountNameFrom)
                ?: throw RuntimeException("找不到资产 key=accountname;value=${billModel.accountNameFrom}")


        // double r38,
        val money = billModel.money

        // java.util.Calendar r40,
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = billModel.time

        //com.mutangtech.qianji.data.model.CurrencyExtra r41
        val currencyExtraInstance =
            XposedHelpers.callMethod(selectBills.first(), "getCurrencyExtra")

        //java.lang.String r42,
        val str = ""

        // java.util.List<java.lang.String> r43, 这是图片链接
        val listStr = arrayListOf<String>()


        // java.util.List<? extends com.mutangtech.qianji.data.model.Tag> r44
        val listTag = arrayListOf<Any>()

        XposedHelpers.callMethod(
            baoXiaoInstance,
            "doBaoXiao",
            set,
            asset,
            money,
            calendar,
            currencyExtraInstance,
            str,
            listStr,
            listTag
        )

    }

    companion object {
        fun convert2Bill(anyBills: List<*>): ArrayList<BookBillModel> {
            val bills = arrayListOf<BookBillModel>()
            anyBills.forEach {
                if (it == null) {
                    return@forEach
                }
                val bill = BookBillModel()
                val billModel = Bill.fromObject(it)
                bill.money = billModel.getMoney()
                bill.remoteId = billModel.get_id().toString()
                bill.remark = billModel.getRemark() ?: ""
                bill.time = billModel.getTimeInSec() * 1000
                bill.remoteBookId = billModel.getBookId().toString()
                bill.category = billModel.getCategory()?.getName() ?: ""
                bills.add(bill)

                // 债务账单
            }
            return bills
        }
    }
}