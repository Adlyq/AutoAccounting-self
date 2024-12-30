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

package net.ankio.auto.xposed.hooks.qianji.hooks

import android.app.Application
import android.content.Context
import android.net.Uri
import de.robv.android.xposed.XposedHelpers
import net.ankio.auto.BuildConfig
import net.ankio.auto.xposed.core.api.HookerManifest
import net.ankio.auto.xposed.core.api.PartHooker
import net.ankio.auto.xposed.core.hook.Hooker
import net.ankio.auto.xposed.core.utils.AppRuntime
import net.ankio.auto.xposed.core.utils.AppRuntime.application
import net.ankio.auto.xposed.core.utils.AppRuntime.classLoader
import net.ankio.auto.xposed.core.utils.AppRuntime.manifest
import net.ankio.auto.xposed.core.utils.MessageUtils
import net.ankio.auto.xposed.core.utils.ThreadUtils
import net.ankio.auto.xposed.hooks.qianji.sync.BaoXiaoUtils
import net.ankio.auto.xposed.hooks.qianji.sync.debt.ExpendLendingUtils
import net.ankio.auto.xposed.hooks.qianji.sync.debt.ExpendRepaymentUtils
import net.ankio.auto.xposed.hooks.qianji.sync.debt.IncomeLendingUtils
import net.ankio.auto.xposed.hooks.qianji.sync.debt.IncomeRepaymentUtils
import net.ankio.auto.xposed.hooks.qianji.tools.QianJiBillType
import net.ankio.auto.xposed.hooks.qianji.tools.QianJiUri
import net.ankio.dex.model.ClazzField
import net.ankio.dex.model.ClazzMethod
import org.ezbook.server.db.model.BillInfoModel

class AutoHooker : PartHooker() {
    lateinit var addBillIntentAct: Class<*>

    override fun hook() {

        addBillIntentAct = classLoader.loadClass("com.mutangtech.qianji.bill.auto.AddBillIntentAct")

        hookTimeout(manifest, classLoader)

        hookTaskLog(manifest, classLoader, application!!)

        hookTaskLogStatus(manifest, classLoader)
    }

    private fun hookTaskLog(
        hookerManifest: HookerManifest,
        classLoader: ClassLoader,
        context: Context
    ) {



        Hooker.before(
            "com.mutangtech.qianji.bill.auto.AddBillIntentAct",
            manifest.method(
                "AddBillIntentAct",
                "InsertAutoTask",
            ),
            "java.lang.String",
            "com.mutangtech.qianji.data.model.AutoTaskLog"
        ){ param ->
            val msg = param.args[0] as String
            val error = handleError(msg)
            val autoTaskLog = param.args[1] as Any

            val value = XposedHelpers.getObjectField(autoTaskLog, "value") as String
            val uri = Uri.parse(value)
            val billInfo = QianJiUri.toAuto(uri)
            if (billInfo.id < 0) return@before
            ThreadUtils.launch {
                BillInfoModel.status(billInfo.id, false)
            }

            if (error !== msg) {
                XposedHelpers.callMethod(autoTaskLog, "setStatus", 0)
                param.args[0] = error
                hookerManifest.log("Qianji Error: $msg")
                return@before
            }



            XposedHelpers.setObjectField(autoTaskLog, "from", BuildConfig.APPLICATION_ID)


            when (uri.getQueryParameter("type")?.toInt() ?: 0) {
                QianJiBillType.Expend.value,
                QianJiBillType.Transfer.value,
                QianJiBillType.Income.value,
                QianJiBillType.ExpendReimbursement.value
                    -> {
                    hookerManifest.log("Qianji Error: $msg")
                    return@before
                }

                // 支出（借出）
                QianJiBillType.ExpendLending.value -> {
                    ThreadUtils.launch {
                        runCatching {
                            ExpendLendingUtils(hookerManifest, classLoader, context).sync(billInfo)
                        }.onSuccess {
                            MessageUtils.toast("借出成功")
                            BillInfoModel.status(billInfo.id, true)
                        }.onFailure {

                            hookerManifest.logD("借出失败 ${it.message}")
                            hookerManifest.logE(it)
                            MessageUtils.toast("借出失败 ${handleError(it.message ?: "")}")
                            hookerManifest.logE(it)
                        }
                    }
                    param.args[0] = "自动记账正在处理中(借出), 请稍候..."
                    XposedHelpers.callMethod(autoTaskLog, "setStatus", 1)


                }
                // 支出（还款）
                QianJiBillType.ExpendRepayment.value -> {
                    ThreadUtils.launch {
                        runCatching {
                            ExpendRepaymentUtils(hookerManifest, classLoader, context).sync(billInfo)
                        }.onSuccess {
                            MessageUtils.toast("还款成功")
                            BillInfoModel.status(billInfo.id, true)
                        }.onFailure {
                            hookerManifest.logE(it)
                            hookerManifest.logD("还款失败 ${it.message}")
                            MessageUtils.toast("还款失败 ${handleError(it.message ?: "")}")
                            hookerManifest.logE(it)
                        }
                    }
                    param.args[0] = "自动记账正在处理中(还款), 请稍候..."
                    XposedHelpers.callMethod(autoTaskLog, "setStatus", 1)

                }

                // 收入（借入）
                QianJiBillType.IncomeLending.value -> {
                    ThreadUtils.launch {
                        runCatching {
                            IncomeLendingUtils(hookerManifest, classLoader, context).sync(billInfo)
                        }.onSuccess {
                            MessageUtils.toast("借入成功")
                            BillInfoModel.status(billInfo.id, true)
                        }.onFailure {
                            hookerManifest.logE(it)
                            hookerManifest.logD("借入失败 ${it.message}")
                            MessageUtils.toast("借入失败 ${handleError(it.message ?: "")}")
                            hookerManifest.logE(it)
                        }
                    }
                    param.args[0] = "自动记账正在处理中(借入), 请稍候..."
                    XposedHelpers.callMethod(autoTaskLog, "setStatus", 1)

                }
                // 收入（收款）
                QianJiBillType.IncomeRepayment.value -> {
                    ThreadUtils.launch {
                        runCatching {
                            IncomeRepaymentUtils(hookerManifest, classLoader, context).sync(billInfo)
                        }.onSuccess {
                            MessageUtils.toast("收款成功")
                            BillInfoModel.status(billInfo.id, true)
                        }.onFailure {
                            hookerManifest.logE(it)
                            hookerManifest.logD("收款失败 ${it.message}")
                            MessageUtils.toast("收款失败 ${handleError(it.message ?: "")}")
                            hookerManifest.logE(it)
                        }
                    }
                    param.args[0] = "自动记账正在处理中(收款), 请稍候..."
                    XposedHelpers.callMethod(autoTaskLog, "setStatus", 1)

                }
                // 收入（报销)
                QianJiBillType.IncomeReimbursement.value -> {
                    ThreadUtils.launch {
                        runCatching {
                            BaoXiaoUtils(hookerManifest, classLoader).doBaoXiao(billInfo)
                        }.onSuccess {
                            MessageUtils.toast("报销成功")
                            BillInfoModel.status(billInfo.id, true)
                        }.onFailure {
                            hookerManifest.logD("报销失败 ${it.message}")
                            MessageUtils.toast("报销失败 ${handleError(it.message ?: "")}")
                            hookerManifest.logE(it)
                        }
                    }
                    param.args[0] = "自动记账正在报销中, 请稍候..."
                    XposedHelpers.callMethod(autoTaskLog, "setStatus", 1)
                }

            }

        }
    }

    private fun handleError(message: String): String {
        if (message.contains("key=accountname;value=")) {
            val assetName = message.split("value=")[1]
            if (assetName.isEmpty()) return message
            return "钱迹中找不到【${assetName}】资产账户，请先创建。"
        }
        return message
    }

    /*
    * hookTimeout
     */
    private fun hookTimeout(hookerManifest: HookerManifest, classLoader: ClassLoader) {

        val clazz by lazy {
            hookerManifest.clazz("TimeoutApp")
        }

        Hooker.after(
            clazz,
            "timeoutApp",
            String::class.java,
            Long::class.java
        ) { param ->
            val prop = param.args[0] as String
            val timeout = param.args[1] as Long
            if (prop == "auto_task_last_time") {
                hookerManifest.logD("hookTimeout: $prop $timeout")
                param.result = true
            }
        }
    }

    private fun hookTaskLogStatus(hookerManifest: HookerManifest, classLoader: ClassLoader) {


        Hooker.replace(
            "com.mutangtech.qianji.data.model.AutoTaskLog",
            "setStatus",
            Int::class.java
        ) { param ->
            val status = param.args[0] as Int
            val raw = XposedHelpers.getIntField(param.thisObject, "status")
            if (raw != 0) {
                return@replace null
            }
            XposedHelpers.setIntField(param.thisObject, "status", status)
        }


        Hooker.replace(
            "com.mutangtech.qianji.data.model.AutoTaskLog",
            "setFrom",
            String::class.java
        ) { param ->
            XposedHelpers.setObjectField(
                param.thisObject,
                "from",
                BuildConfig.APPLICATION_ID
            )
        }

    }


}