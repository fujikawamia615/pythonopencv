<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { Plus, OfficeBuilding } from "@element-plus/icons-vue";
import { api } from "../api";
const doors = ref([]),
  loading = ref(false),
  dialog = ref(false),
  saving = ref(false),
  editing = ref(null),
  formRef = ref();
const form = reactive({ name: "", location: "", enabled: true });
const rules = {
  name: [
    {
      required: true,
      whitespace: true,
      message: "请输入门禁名称",
      trigger: "blur",
    },
  ],
  location: [
    {
      required: true,
      whitespace: true,
      message: "请输入安装位置",
      trigger: "blur",
    },
  ],
};
async function load() {
  loading.value = true;
  try {
    doors.value = await api("/doors");
  } catch {
  } finally {
    loading.value = false;
  }
}
function edit(row) {
  editing.value = row?.id;
  Object.assign(
    form,
    row
      ? { name: row.name, location: row.location, enabled: row.enabled }
      : { name: "", location: "", enabled: true },
  );
  dialog.value = true;
}
async function save() {
  if (!(await formRef.value.validate().catch(() => false))) return;
  saving.value = true;
  try {
    await api(editing.value ? `/doors/${editing.value}` : "/doors", {
      method: editing.value ? "PUT" : "POST",
      body: form,
    });
    dialog.value = false;
    ElMessage.success("门禁已保存");
    await load();
  } catch {
  } finally {
    saving.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div class="toolbar">
    <p class="muted">每个门禁独立校验授权，停用后拒绝所有通行请求。</p>
    <div class="toolbar-spacer"></div>
    <el-button type="primary" :icon="Plus" @click="edit(null)"
      >新增门禁</el-button
    >
  </div>
  <div v-loading="loading" class="door-grid">
    <el-card v-for="door in doors" :key="door.id" shadow="never"
      ><div class="card-header">
        <span class="door-icon"
          ><el-icon :size="30"><OfficeBuilding /></el-icon></span
        ><el-tag :type="door.enabled ? 'success' : 'info'">{{
          door.enabled ? "已启用" : "已停用"
        }}</el-tag>
      </div>
      <h2>{{ door.name }}</h2>
      <p class="muted">{{ door.location }}</p>
      <el-divider />
      <div class="card-header">
        <span class="muted">模拟门禁 · #{{ door.id }}</span
        ><el-button plain @click="edit(door)">编辑配置</el-button>
      </div></el-card
    >
  </div>
  <el-dialog
    v-model="dialog"
    :title="editing ? '编辑门禁' : '新增门禁'"
    width="480px"
    destroy-on-close
    ><el-form ref="formRef" :model="form" :rules="rules" label-position="top"
      ><el-form-item label="门禁名称" prop="name"
        ><el-input v-model="form.name" maxlength="80" /></el-form-item
      ><el-form-item label="安装位置" prop="location"
        ><el-input v-model="form.location" maxlength="120" /></el-form-item
      ><el-form-item label="门禁状态"
        ><el-switch
          v-model="form.enabled"
          active-text="启用"
          inactive-text="停用" /></el-form-item></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save"
        >保存</el-button
      ></template
    ></el-dialog
  >
</template>
