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

package org.ezbook.server.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import org.ezbook.server.db.model.AssetsModel


@Dao
interface AssetsDao {

    //根据条件查询
    @Query("SELECT * FROM AssetsModel ORDER BY id DESC ")
    suspend fun load(): List<AssetsModel>

    //根据条件查询
    @Query("SELECT * FROM AssetsModel WHERE type = :type ORDER BY id DESC")
    suspend fun load(type: String): List<AssetsModel>


    // 统计总数
    @Query("SELECT COUNT(*) FROM AssetsModel")
    suspend fun count(): Int

    @Insert
    suspend fun insert(log: AssetsModel): Long

    @Query("DELETE FROM AssetsModel")
    suspend fun clear()

    @Transaction
    suspend fun put(data: Array<AssetsModel>) {
        clear()
        data.forEach {
            insert(it)
        }
    }

    @Query("SELECT * FROM AssetsModel WHERE name = :name limit 1")
    suspend fun query(name: String): AssetsModel?
}