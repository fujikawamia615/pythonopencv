<script setup>
import { onMounted, ref } from "vue";
import { Refresh, Search } from "@element-plus/icons-vue";
import { api, reasons, formatTime, scoreText } from "../api";
const items = ref([]),
  total = ref(0),
  page = ref(1),
  search = ref(""),
  allowed = ref(""),
  loading = ref(false);
async function load() {
  loading.value = true;
  const query = new URLSearchParams({
    search: search.value,
    page: page.value,
    size: 10,
  });
  if (allowed.value !== "") query.set("allowed", allowed.value);
  try {
    const result = await api(`/logs?${query}`);
    items.value = result.items;
    total.value = result.total;
  } catch {
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <el-card shadow="never"
    ><div class="toolbar">
      <el-input
        v-model="search"
        :prefix-icon="Search"
        clearable
        placeholder="姓名、编号、门禁或原因代码"
        maxlength="80"
        @keyup.enter="
          page = 1;
          load();
        "
        @clear="
          page = 1;
          load();
        "
      /><el-select
        v-model="allowed"
        style="width: 140px"
        @change="
          page = 1;
          load();
        "
        ><el-option label="全部结果" value="" /><el-option
          label="允许通行"
          value="true" /><el-option label="拒绝通行" value="false" /></el-select
      ><el-button
        @click="
          page = 1;
          load();
        "
        >查询</el-button
      >
      <div class="toolbar-spacer"></div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>
    <el-table v-loading="loading" :data="items" empty-text="暂无通行记录"
      ><el-table-column prop="id" label="编号" width="80" /><el-table-column
        label="人员"
        min-width="110"
        ><template #default="{ row }">{{
          row.personName || "未识别人员"
        }}</template></el-table-column
      ><el-table-column
        prop="personNo"
        label="人员编号"
        min-width="120"
      /><el-table-column
        prop="doorName"
        label="门禁"
        min-width="130"
      /><el-table-column label="通行结果" width="110"
        ><template #default="{ row }"
          ><el-tag :type="row.allowed ? 'success' : 'danger'">{{
            row.allowed ? "允许" : "拒绝"
          }}</el-tag></template
        ></el-table-column
      ><el-table-column label="原因" min-width="140"
        ><template #default="{ row }">{{
          reasons[row.reason] || row.reason
        }}</template></el-table-column
      ><el-table-column label="余弦相似度" width="120"
        ><template #default="{ row }">{{
          scoreText(row.score)
        }}</template></el-table-column
      ><el-table-column label="通行时间" min-width="175"
        ><template #default="{ row }">{{
          formatTime(row.createdAt)
        }}</template></el-table-column
      ></el-table
    >
    <div class="pagination">
      <el-pagination
        v-model:current-page="page"
        :page-size="10"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
      />
    </div>
  </el-card>
</template>
