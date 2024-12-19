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

package net.ankio.auto.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import net.ankio.auto.R
import net.ankio.auto.databinding.FragmentCategoryRuleBinding
import net.ankio.auto.ui.adapter.CategoryRuleAdapter
import net.ankio.auto.ui.api.BasePageFragment
import net.ankio.auto.ui.utils.viewBinding
import org.ezbook.server.db.model.CategoryRuleModel

class CategoryRuleFragment : BasePageFragment<CategoryRuleModel>() {
    override suspend fun loadData(callback: (resultData: List<CategoryRuleModel>) -> Unit) {
        val newData = CategoryRuleModel.list(page, pageSize)
        callback(newData)
    }

    override fun onCreateAdapter() {
        val recyclerView = statusPage.contentView!!
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = CategoryRuleAdapter(pageData, requireActivity()) { item, position ->
            val bundle = Bundle().apply {
                putString("data", item.toJson())
            }
            navigate(R.id.action_categoryRuleFragment_to_categoryEditFragment, bundle)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.addButton.setOnClickListener {
            navigate(R.id.action_categoryRuleFragment_to_categoryEditFragment)
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = binding.root

    override val binding: FragmentCategoryRuleBinding by viewBinding(FragmentCategoryRuleBinding::inflate)


}