/*
 * Copyright (C) 2023 ankio(ankio@ankio.net)
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

package net.ankio.auto.ui.adapter

import android.content.Intent
import android.net.Uri
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ankio.auto.App
import net.ankio.auto.R
import net.ankio.auto.databinding.AdapterDataBinding
import net.ankio.auto.request.Pastebin
import net.ankio.auto.service.FloatingWindowService
import net.ankio.auto.storage.ConfigUtils
import net.ankio.auto.storage.Logger
import net.ankio.auto.ui.api.BaseActivity
import net.ankio.auto.ui.api.BaseAdapter
import net.ankio.auto.ui.api.BaseViewHolder
import net.ankio.auto.ui.dialog.DataEditorDialog
import net.ankio.auto.ui.dialog.DataIssueDialog
import net.ankio.auto.ui.utils.LoadingUtils
import net.ankio.auto.ui.utils.ToastUtils
import net.ankio.auto.utils.CustomTabsHelper
import net.ankio.auto.utils.DateUtils
import org.ezbook.server.Server
import org.ezbook.server.constant.Setting
import org.ezbook.server.db.model.AppDataModel
import org.ezbook.server.db.model.BillInfoModel

class AppDataAdapter(
    private val list: MutableList<AppDataModel>,
    private val activity: BaseActivity
) : BaseAdapter<AdapterDataBinding, AppDataModel>(AdapterDataBinding::class.java, list) {


    private val version = ConfigUtils.getString(Setting.RULE_VERSION, "0")

    private suspend fun tryAdaptUnmatchedItems(
        holder: BaseViewHolder<AdapterDataBinding, AppDataModel>
    ) = withContext(Dispatchers.IO) {
        val item = holder.item!!

        val billModel = testRule(item)
        if (billModel == null) {
            // update version
            item.version = version
            AppDataModel.put(item)
            return@withContext
        }
        item.match = true
        item.rule = billModel.ruleName
        item.version = version
        val position = indexOf(item)
        if (position != -1 && position < list.size) {
            AppDataModel.put(item)
            Logger.d("Identification successful！${item.data}")
            withContext(Dispatchers.Main) {
                list[position] = item
                notifyItemChanged(position)
            }
        } else {
            Logger.d("Invalid position: $position")
        }
    }

    private suspend fun testRule(item: AppDataModel,ai: Boolean = false): BillInfoModel? = withContext(Dispatchers.IO) {
        val result = Server.request(
            "js/analysis?type=${item.type.name}&app=${item.app}&fromAppData=true&ai=${ai}",
            item.data
        )
            ?: return@withContext null
        Logger.d("Test Result: $result")
        val data = Gson().fromJson(result, JsonObject::class.java)
        if (data.get("code").asInt != 200) {
            Logger.w("Test Error Info: ${data.get("msg").asString}")
            return@withContext null
        }
        return@withContext Gson().fromJson(data.getAsJsonObject("data"), BillInfoModel::class.java)
    }


    private fun buildUploadDialog(holder: BaseViewHolder<AdapterDataBinding, AppDataModel>) {
        val item = holder.item!!
        DataEditorDialog(activity, item.data) { result ->
            val loading = LoadingUtils(activity)
            loading.show(R.string.upload_waiting)
            holder.launch {
                val type = item.type.name
                val title = "[Adaptation Request][$type]${item.app}"
                runCatching {
                    val (url,timeout) = Pastebin.add(result,holder.context)
                    val body = """
<!------ 
 1. 请不要手动复制数据，下面的链接中已包含数据；
 2. 您可以新增信息，但是不要删除本页任何内容；
 3. 一般情况下，您直接划到底部点击submit即可。
 ------>                        
## 数据链接                        
[数据过期时间：${timeout}](${url})
## 其他信息

                """.trimIndent()
                    CustomTabsHelper.launchUrl(
                        activity,
                        Uri.parse("$githubAutoRule/new?title=${Uri.encode(title)}&body=${Uri.encode(body)}"),
                    )
                    loading.close()
                }.onFailure {
                    ToastUtils.error(it.message!!)
                    loading.close()
                    return@launch
                }
            }
        }.show(float = false)
    }

    private fun buildIssueDialog(holder: BaseViewHolder<AdapterDataBinding, AppDataModel>) {
        val item = holder.item!!

        DataIssueDialog(activity) { issue ->
            DataEditorDialog(activity, item.data) { result ->
                val loading = LoadingUtils(activity)
                loading.show(R.string.upload_waiting)
                holder.launch {
                    val type = item.type.name
                    val title = "[Bug][Rule][$type]${item.app}"
                    runCatching {
                        val (url,timeout) = Pastebin.add(result,holder.context)
                        val body = """
<!------ 
 1. 请不要手动复制数据，下面的链接中已包含数据；
 2. 该功能是反馈规则识别错误的，请勿写其他无关内容；
 3. 一般情况下，您直接划到底部点击submit即可。
 ------>  
## 规则
${item.rule}
## 说明
$issue
## 数据
[数据过期时间：${timeout}](${url})
                         
                                            """.trimIndent()

                        CustomTabsHelper.launchUrl(
                            activity,
                            Uri.parse("$githubAutoAccounting/new?title=${Uri.encode(title)}&body=${Uri.encode(body)}"),
                        )
                        loading.close()
                    }.onFailure {
                        ToastUtils.error(it.message!!)
                        loading.close()
                        return@launch
                    }

                }
            }.show(float = false)

        }.show(float = false)


    }

    private val githubAutoAccounting = "https://github.com/AutoAccountingOrg/AutoAccounting/issues"
    private val githubAutoRule = "https://github.com/AutoAccountingOrg/AutoRule/issues"


    private suspend fun runTest(ai:Boolean = false,item: AppDataModel){
        val loadingUtils = LoadingUtils(activity)
        if (ai){
            loadingUtils.show(activity.getString(R.string.ai_loading,ConfigUtils.getString(Setting.AI_MODEL)))
        }
        val billModel = testRule(item,ai)
        if (ai){
            loadingUtils.close()
        }
        if (billModel == null) {
            ToastUtils.error(R.string.no_rule_hint)
        } else {
            val serviceIntent =
                Intent(activity, FloatingWindowService::class.java).apply {
                    putExtra("parent", "")
                    putExtra("billInfo", Gson().toJson(billModel))
                    putExtra("showWaitTip", false)
                    putExtra("from","AppData")
                }
            Logger.d("Start FloatingWindowService")
            activity.startService(serviceIntent)
        }
    }

    override fun onInitViewHolder(holder: BaseViewHolder<AdapterDataBinding, AppDataModel>) {
        val binding = holder.binding

        binding.testRuleAi.setOnClickListener{
            val item = holder.item!!
            holder.launch {
                runTest(true,item)
            }
        }

        binding.testRule.setOnClickListener {
            val item = holder.item!!
            holder.launch {
               runTest(false,item)
            }

        }

        binding.content.setOnClickListener {
            MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.content_title))
                .setMessage(binding.content.text.toString())
                .setNegativeButton(activity.getString(R.string.cancel_msg)) { _, _ -> }
                .setPositiveButton(activity.getString(R.string.copy)) { _, _ ->
                    App.copyToClipboard(binding.content.text.toString())
                    ToastUtils.error(R.string.copy_command_success)
                }
                .show()
        }

        binding.uploadData.setOnClickListener {
            val item = holder.item!!

            if (!item.match) {
                buildUploadDialog(holder)
                return@setOnClickListener
            }
            buildIssueDialog(holder)

        }

        binding.root.setOnLongClickListener {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.delete_title)
                .setMessage(R.string.delete_data_message)
                .setPositiveButton(R.string.sure_msg) { _, _ ->
                    val item = holder.item!!
                    val position = indexOf(item)
                    list.removeAt(position)
                    notifyItemRemoved(position)
                    holder.launch {
                        AppDataModel.delete(item.id)
                    }
                }
                .setNegativeButton(R.string.cancel_msg) { _, _ -> }
                .show()
            true
        }

        binding.groupCard.setCardBackgroundColor(SurfaceColors.SURFACE_1.getColor(activity))
        binding.content.setBackgroundColor(SurfaceColors.SURFACE_3.getColor(activity))

    }

    override fun onBindViewHolder(
        holder: BaseViewHolder<AdapterDataBinding, AppDataModel>,
        data: AppDataModel,
        position: Int
    ) {
        val binding = holder.binding
        binding.content.text = data.data
        binding.uploadData.visibility = View.VISIBLE

        binding.testRuleAi.visibility = if (ConfigUtils.getBoolean(Setting.USE_AI,false)) View.VISIBLE else View.GONE

        if (!data.match || data.rule.isEmpty()) {
            if (version != data.version) {
                holder.launch {
                    tryAdaptUnmatchedItems(holder)
                }
            }
        }


        binding.time.setText(DateUtils.stampToDate(data.time))

        if (!data.match || data.rule.isEmpty()) {
            binding.rule.visibility = View.GONE
            binding.uploadData.setIconResource(R.drawable.icon_upload)
        } else {
            binding.rule.visibility = View.VISIBLE
            binding.uploadData.setIconResource(R.drawable.icon_question)
        }
        val rule = data.rule

        val regex = "\\[(.*?)]".toRegex()
        val matchResult = regex.find(rule)
        if (matchResult != null) {
            val (value) = matchResult.destructured
            binding.rule.setText(value)
        } else {
            binding.rule.setText(data.rule)
        }
    }
}
