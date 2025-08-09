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

package org.ezbook.server.server

import io.ktor.application.call
import io.ktor.request.receive
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import org.ezbook.server.Server
import org.ezbook.server.db.Db
import org.ezbook.server.db.model.TagModel
import org.ezbook.server.models.ResultModel

/**
 * 标签管理路由配置
 * 提供标签的增删改查功能
 */
fun Route.tagRoutes() {
    route("/tag") {
        /**
         * GET /tag/list - 获取标签列表
         * 支持分页查询和搜索功能
         *
         * @param page 页码，默认为1（当limit=0时忽略分页）
         * @param limit 每页条数，默认为10，设为0时返回所有数据
         * @param search 搜索关键词，可选
         * @return ResultModel 包含标签列表数据
         */
        get("/list") {
            val limit = call.request.queryParameters["limit"]?.toInt() ?: 10

            if (limit == 0) {
                // 返回所有数据，不分页
                val tags = Db.get().tagDao().list()
                call.respond(ResultModel(200, "OK", tags))
                return@get
            }

            val page = call.request.queryParameters["page"]?.toInt() ?: 1
            val offset = (page - 1) * limit
            val search = call.request.queryParameters["search"]?.takeIf { it.isNotEmpty() }

            val tags = if (search != null) {
                Db.get().tagDao().search(search, limit, offset)
            } else {
                Db.get().tagDao().load(limit, offset)
            }
            call.respond(ResultModel(200, "OK", tags))
        }

        /**
         * POST /tag/put - 保存或更新标签
         * 根据标签ID自动判断是插入新标签还是更新现有标签
         * 标签名称不允许重复
         *
         * @param body TagModel 标签数据
         * @return ResultModel 包含标签ID
         */
        post("/put") {
            val model = call.receive(TagModel::class)

            // 检查标签名称是否重复（排除自己）
            val existingTag = Db.get().tagDao().queryByName(model.name)
            if (existingTag != null && existingTag.id != model.id) {
                call.respond(ResultModel(400, "标签名称已存在", null))
                return@post
            }

            // 设置更新时间
            model.updateTime = System.currentTimeMillis()

            if (model.id == 0L) {
                // 新增标签
                model.createTime = System.currentTimeMillis()
                model.id = Db.get().tagDao().insert(model)
            } else {
                // 更新标签
                Db.get().tagDao().update(model)
            }

            call.respond(ResultModel(200, "OK", model.id))
        }

        /**
         * POST /tag/delete - 删除指定标签
         *
         * @param body 包含id的JSON对象
         * @return ResultModel 包含删除的标签ID
         */
        post("/delete") {
            val requestBody = call.receiveText()
            val json =
                com.google.gson.Gson().fromJson(requestBody, com.google.gson.JsonObject::class.java)
            val id = json?.get("id")?.asLong ?: 0

            Db.get().tagDao().delete(id)
            call.respond(ResultModel(200, "OK", id))
        }

        /**
         * GET /tag/get - 获取指定ID的标签
         *
         * @param id 标签ID
         * @return ResultModel 包含标签信息
         */
        get("/get") {
            val id = call.request.queryParameters["id"]?.toLongOrNull() ?: 0
            val tag = Db.get().tagDao().query(id)
            call.respond(ResultModel(200, "OK", tag))
        }

        /**
         * GET /tag/count - 获取标签总数
         *
         * @return ResultModel 包含标签总数
         */
        get("/count") {
            val count = Db.get().tagDao().count()
            call.respond(ResultModel(200, "OK", count))
        }

        /**
         * GET /tag/check - 检查标签名称是否可用
         *
         * @param name 标签名称
         * @param id 标签ID（可选，用于排除自己）
         * @return ResultModel 包含是否可用的布尔值
         */
        get("/check") {
            val name = call.request.queryParameters["name"] ?: ""
            val id = call.request.queryParameters["id"]?.toLongOrNull() ?: 0

            val existingTag = Db.get().tagDao().queryByName(name)
            val isAvailable = existingTag == null || existingTag.id == id

            call.respond(ResultModel(200, "OK", isAvailable))
        }
    }
}
