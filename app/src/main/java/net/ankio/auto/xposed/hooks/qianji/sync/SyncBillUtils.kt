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

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.ankio.auto.xposed.core.api.HookerManifest
import net.ankio.auto.xposed.core.utils.MessageUtils
import net.ankio.auto.xposed.hooks.qianji.tools.QianJiUri
import net.ankio.auto.xposed.hooks.qianji.tools.UserUtils
import org.ezbook.server.constant.BillType
import org.ezbook.server.db.model.BillInfoModel

class SyncBillUtils(
    private val manifest: HookerManifest,
    private val classLoader: ClassLoader
) {
    suspend fun sync(context: Context) = withContext(Dispatchers.IO) {
        if (!UserUtils(manifest, classLoader).isLogin()) {
            MessageUtils.toast("未登录无法自动记账")
            return@withContext
        }
        val bills = BillInfoModel.sync()
        if (bills.isEmpty()) {
            manifest.log("No bills need to sync")
            return@withContext
        }
        manifest.log("Sync ${bills.size} bills")
        AutoConfig.load()
        bills.forEach {

            if (!AutoConfig.assetManagement){
                if (it.type === BillType.Transfer){
                    //没开启资产管理不同步转账类型
                    return@forEach
                }
            }

            if (!AutoConfig.lending){
                if (it.type === BillType.ExpendLending || it.type === BillType.IncomeLending ||
                    it.type === BillType.ExpendRepayment || it.type === BillType.IncomeRepayment){
                    //没开启债务不同步报销类型
                    return@forEach
                }
            }

            val bill = QianJiUri.toQianJi(it)
            val intent = Intent(Intent.ACTION_VIEW, bill)
            intent.putExtra("billInfo", Gson().toJson(it))
            withContext(Dispatchers.Main) {
                context.startActivity(intent)
            }
            // 现将指定的账单全部记录为成功，后续拦截到错误的时候再修改为失败
            BillInfoModel.status(it.id, true)
            delay(5) // 延迟5毫秒
        }
        withContext(Dispatchers.Main) {
            MessageUtils.toast("已将所有账单同步完成！")
        }
    }
}