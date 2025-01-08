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

package org.ezbook.server.routes

import io.ktor.application.ApplicationCall
import io.ktor.http.Parameters
import io.ktor.request.receive
import org.ezbook.server.constant.Setting
import org.ezbook.server.db.Db
import org.ezbook.server.db.model.BookBillModel
import org.ezbook.server.models.ResultModel

class BookBillRoute(private val session: ApplicationCall) {
    private val params: Parameters = session.request.queryParameters

    suspend fun list(): ResultModel {
        val type = params["type"] ?: ""
        val data = Db.get().bookBillDao().list(type)
        return ResultModel(200, "OK", data)
    }


    suspend fun put(): ResultModel {
        val md5 = params["md5"] ?: ""
        val type = params["type"] ?: ""
        val json = session.receive<Array<BookBillModel>>()
        Db.get().bookBillDao().put(json.toList(), type)
        SettingRoute.setByInner(type, md5)
        return ResultModel(200, "OK")
    }

}