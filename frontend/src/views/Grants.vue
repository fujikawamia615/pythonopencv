<script setup>
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus } from "@element-plus/icons-vue";
import { api, formatTime } from "../api";
const grants = ref([]),
  persons = ref([]),
  doors = ref([]),
  loading = ref(false),
  saving = ref(false),
  dialog = ref(false),
  formRef = ref(),
  searching = ref(false);
const form = reactive({ personId: null, doorId: null, range: [] });
const rules = {
  personId: [{ required: true, message: "请选择人员", trigger: "change" }],
  doorId: [{ required: true, message: "请选择门禁", trigger: "change" }],
  range: [
    {
      type: "array",
      required: true,
      len: 2,
      message: "请选择完整有效期",
      trigger: "change",
    },
  ],
};
function localDate(date) {
  const pad = (n) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}
async function searchPersons(query = "") {
  searching.value = true;
  try {
    persons.value = (
      await api(`/persons?${new URLSearchParams({ search: query, size: 100 })}`)
    ).items;
  } catch {
  } finally {
    searching.value = false;
  }
}
async function load() {
  loading.value = true;
  try {
    [grants.value, doors.value] = await Promise.all([
      api("/grants"),
      api("/doors"),
    ]);
  } catch {
  } finally {
    loading.value = false;
  }
}
async function edit(row) {
  await searchPersons();
  if (row && !persons.value.some((p) => p.id === row.personId))
    persons.value.push({
      id: row.personId,
      name: row.personName,
      personNo: row.personNo,
    });
  const start = new Date(),
    end = new Date(Date.now() + 30 * 86400000);
  Object.assign(form, {
    personId: row?.personId || null,
    doorId: row?.doorId || doors.value[0]?.id || null,
    range: row
      ? [row.validFrom, row.validUntil]
      : [localDate(start), localDate(end)],
  });
  dialog.value = true;
}
async function save() {
  if (!(await formRef.value.validate().catch(() => false))) return;
  if (form.range[1] <= form.range[0])
    return ElMessage.warning("结束时间必须晚于开始时间");
  saving.value = true;
  try {
    await api("/grants", {
      method: "POST",
      body: {
        personId: form.personId,
        doorId: form.doorId,
        validFrom: form.range[0],
        validUntil: form.range[1],
      },
    });
    dialog.value = false;
    ElMessage.success("通行授权已保存");
    await load();
  } catch {
  } finally {
    saving.value = false;
  }
}
async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `撤销 ${row.personName} 对“${row.doorName}”的通行权限？`,
      "撤销授权",
      { type: "warning" },
    );
  } catch {
    return;
  }
  try {
    await api(`/grants/${row.id}`, { method: "DELETE" });
    ElMessage.success("已撤销授权");
    await load();
  } catch {}
}
onMounted(load);
</script>
<template>
  <el-card shadow="never"
    ><div class="toolbar">
      <p class="muted">
        同一人员与门禁再次授权，将更新有效期。时间按 Asia/Shanghai 解释。
      </p>
      <div class="toolbar-spacer"></div>
      <el-button type="primary" :icon="Plus" @click="edit(null)"
        >新增授权</el-button
      >
    </div>
    <el-table
      v-loading="loading"
      :data="grants"
      empty-text="暂无授权，请为已录入人员配置权限"
      ><el-table-column
        prop="personName"
        label="人员"
        min-width="100"
      /><el-table-column
        prop="personNo"
        label="人员编号"
        min-width="120"
      /><el-table-column
        prop="doorName"
        label="门禁"
        min-width="130"
      /><el-table-column label="开始时间" min-width="170"
        ><template #default="{ row }">{{
          formatTime(row.validFrom)
        }}</template></el-table-column
      ><el-table-column label="结束时间" min-width="170"
        ><template #default="{ row }">{{
          formatTime(row.validUntil)
        }}</template></el-table-column
      ><el-table-column label="操作" width="140" fixed="right"
        ><template #default="{ row }"
          ><el-button link type="primary" @click="edit(row)">修改</el-button
          ><el-button link type="danger" @click="remove(row)"
            >撤销</el-button
          ></template
        ></el-table-column
      ></el-table
    ></el-card
  >
  <el-dialog
    v-model="dialog"
    title="配置通行授权"
    width="620px"
    destroy-on-close
    :close-on-click-modal="false"
    ><el-form ref="formRef" :model="form" :rules="rules" label-position="top"
      ><el-form-item label="人员" prop="personId"
        ><el-select
          v-model="form.personId"
          filterable
          remote
          :remote-method="searchPersons"
          :loading="searching"
          placeholder="输入姓名或编号搜索"
          class="full-width"
          ><el-option
            v-for="person in persons"
            :key="person.id"
            :label="`${person.name} · ${person.personNo}`"
            :value="person.id" /></el-select></el-form-item
      ><el-form-item label="门禁" prop="doorId"
        ><el-select v-model="form.doorId" class="full-width"
          ><el-option
            v-for="door in doors"
            :key="door.id"
            :label="door.name"
            :value="door.id" /></el-select></el-form-item
      ><el-form-item label="有效期（Asia/Shanghai）" prop="range"
        ><el-date-picker
          v-model="form.range"
          type="datetimerange"
          value-format="YYYY-MM-DDTHH:mm:ss"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          range-separator="至"
          class="full-width" /></el-form-item></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save"
        >保存授权</el-button
      ></template
    ></el-dialog
  >
</template>
