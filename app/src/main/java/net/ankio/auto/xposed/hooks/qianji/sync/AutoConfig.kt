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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ezbook.server.constant.Setting
import net.ankio.auto.http.api.SettingAPI

object AutoConfig {
    var assetManagement: Boolean = true//是否开启资产管理
    var multiCurrency: Boolean = true//是否开启多币种
    var reimbursement: Boolean = true//是否开启报销
    var lending: Boolean = true//是否开启债务功能
    var multiBooks: Boolean = true//是否开启多账本
    var fee: Boolean = true//是否开启手续费
    suspend fun load() = withContext(Dispatchers.IO) {
        assetManagement = SettingAPI.get(Setting.SETTING_ASSET_MANAGER, "true") != "false"
        multiCurrency = SettingAPI.get(Setting.SETTING_CURRENCY_MANAGER, "true") != "false"
        reimbursement = SettingAPI.get(Setting.SETTING_REIMBURSEMENT, "true") != "false"
        lending = SettingAPI.get(Setting.SETTING_DEBT, "true") != "false"
        multiBooks = SettingAPI.get(Setting.SETTING_BOOK_MANAGER, "true") != "false"
        fee = SettingAPI.get(Setting.SETTING_FEE, "true") != "false"
    }
}