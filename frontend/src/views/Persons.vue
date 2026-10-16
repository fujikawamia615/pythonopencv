<script setup>
import { reactive, ref, onMounted, onBeforeUnmount } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Search, UploadFilled } from "@element-plus/icons-vue";
import { api, imageForm, formatTime } from "../api";
const items = ref([]),
  total = ref(0),
  page = ref(1),
  search = ref(""),
  loading = ref(false);
const dialog = ref(false),
  faceDialog = ref(false),
  editing = ref(null),
  selected = ref(null),
  saving = ref(false);
const formRef = ref(),
  uploadRef = ref(),
  file = ref(null),
  preview = ref("");
const form = reactive({
  personNo: "",
  name: "",
  department: "",
  enabled: true,
});
const rules = {
  personNo: [
    {
      required: true,
      whitespace: true,
      message: "请输入人员编号",
      trigger: "blur",
    },
  ],
  name: [
    {
      required: true,
      whitespace: true,
      message: "请输入姓名",
      trigger: "blur",
    },
  ],
};
async function load() {
  loading.value = true;
  try {
    const result = await api(
      `/persons?${new URLSearchParams({ search: search.value, page: page.value, size: 10 })}`,
    );
    items.value = result.items;
    total.value = result.total;
  } catch {
  } finally {
    loading.value = false;
  }
}
function edit(row) {
  editing.value = row?.id || null;
  Object.assign(
    form,
    row
      ? {
          personNo: row.personNo,
          name: row.name,
          department: row.department,
          enabled: row.enabled,
        }
      : { personNo: "", name: "", department: "", enabled: true },
  );
  dialog.value = true;
}
async function save() {
  if (!(await formRef.value.validate().catch(() => false))) return;
  saving.value = true;
  try {
    await api(editing.value ? `/persons/${editing.value}` : "/persons", {
      method: editing.value ? "PUT" : "POST",
      body: form,
    });
    ElMessage.success("人员信息已保存");
    dialog.value = false;
    await load();
  } catch {
  } finally {
    saving.value = false;
  }
}
async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `删除“${row.name}”及其人脸和授权？历史通行记录将保留。`,
      "删除人员",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消" },
    );
  } catch {
    return;
  }
  try {
    await api(`/persons/${row.id}`, { method: "DELETE" });
    ElMessage.success("已删除");
    if (items.value.length === 1 && page.value > 1) page.value--;
    await load();
  } catch {}
}
function clearFile() {
  if (preview.value) URL.revokeObjectURL(preview.value);
  preview.value = "";
  file.value = null;
}
function enroll(row) {
  selected.value = row;
  clearFile();
  faceDialog.value = true;
}
function choose(upload) {
  clearFile();
  if (
    !["image/jpeg", "image/png"].includes(upload.raw.type) ||
    upload.raw.size > 5 * 1024 * 1024
  ) {
    ElMessage.warning("请选择不超过 5 MB 的 JPEG / PNG 图片");
    uploadRef.value?.clearFiles();
    return;
  }
  file.value = upload.raw;
  preview.value = URL.createObjectURL(file.value);
}
async function saveFace() {
  if (!file.value) return ElMessage.warning("请先选择人脸图片");
  saving.value = true;
  try {
    await api(`/persons/${selected.value.id}/face`, {
      method: "POST",
      body: imageForm(file.value),
    });
    ElMessage.success("人脸特征已录入");
    faceDialog.value = false;
    await load();
  } catch {
  } finally {
    saving.value = false;
  }
}
async function removeFace() {
  try {
    await ElMessageBox.confirm(
      "清除该人员的人脸特征？之后需要重新录入才能识别。",
      "清除人脸",
      { type: "warning" },
    );
  } catch {
    return;
  }
  saving.value = true;
  try {
    await api(`/persons/${selected.value.id}/face`, { method: "DELETE" });
    faceDialog.value = false;
    ElMessage.success("人脸已清除");
    await load();
  } catch {
  } finally {
    saving.value = false;
  }
}
onMounted(load);
onBeforeUnmount(clearFile);
</script>
<template>
  <el-card shadow="never"
    ><div class="toolbar">
      <el-input
        v-model="search"
        placeholder="搜索姓名、编号或部门"
        :prefix-icon="Search"
        clearable
        maxlength="80"
        @keyup.enter="
          page = 1;
          load();
        "
        @clear="
          page = 1;
          load();
        "
      /><el-button
        @click="
          page = 1;
          load();
        "
        >查询</el-button
      >
      <div class="toolbar-spacer"></div>
      <el-button type="primary" :icon="Plus" @click="edit(null)"
        >新增人员</el-button
      >
    </div>
    <el-table
      v-loading="loading"
      :data="items"
      empty-text="暂无人员，点击右上角新增"
      ><el-table-column
        prop="personNo"
        label="人员编号"
        min-width="120"
      /><el-table-column
        prop="name"
        label="姓名"
        min-width="100"
      /><el-table-column
        prop="department"
        label="部门"
        min-width="120"
      /><el-table-column label="人脸状态" width="120"
        ><template #default="{ row }"
          ><el-tag :type="row.enrolled ? 'success' : 'info'">{{
            row.enrolled ? "已录入" : "未录入"
          }}</el-tag></template
        ></el-table-column
      ><el-table-column label="账号状态" width="110"
        ><template #default="{ row }"
          ><el-tag :type="row.enabled ? 'success' : 'danger'" effect="plain">{{
            row.enabled ? "启用" : "禁用"
          }}</el-tag></template
        ></el-table-column
      ><el-table-column label="操作" width="230" fixed="right"
        ><template #default="{ row }"
          ><el-button link type="primary" @click="enroll(row)"
            >人脸录入</el-button
          ><el-button link type="primary" @click="edit(row)">编辑</el-button
          ><el-button link type="danger" @click="remove(row)"
            >删除</el-button
          ></template
        ></el-table-column
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
  <el-dialog
    v-model="dialog"
    :title="editing ? '编辑人员' : '新增人员'"
    width="480px"
    destroy-on-close
    :close-on-click-modal="false"
    ><el-form ref="formRef" :model="form" :rules="rules" label-position="top"
      ><el-form-item label="人员编号" prop="personNo"
        ><el-input
          v-model="form.personNo"
          maxlength="40"
          placeholder="例如 S2026001" /></el-form-item
      ><el-form-item label="姓名" prop="name"
        ><el-input v-model="form.name" maxlength="50" /></el-form-item
      ><el-form-item label="部门 / 班级"
        ><el-input
          v-model="form.department"
          maxlength="80"
          placeholder="例如 计算机学院" /></el-form-item
      ><el-form-item label="启用状态"
        ><el-switch
          v-model="form.enabled"
          active-text="允许参与通行鉴权" /></el-form-item></el-form
    ><template #footer
      ><el-button @click="dialog = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save"
        >保存</el-button
      ></template
    ></el-dialog
  >
  <el-dialog
    v-model="faceDialog"
    title="录入人脸特征"
    width="540px"
    destroy-on-close
    :close-on-click-modal="false"
    @closed="clearFile"
    ><div v-if="selected">
      <p>
        <strong>{{ selected.name }}</strong> · {{ selected.personNo }}
      </p>
      <p class="muted">
        {{
          selected.enrolled
            ? `上次录入：${formatTime(selected.enrolledAt)}。重新上传会覆盖原特征。`
            : "尚未录入人脸。"
        }}
      </p>
    </div>
    <el-upload
      ref="uploadRef"
      drag
      :auto-upload="false"
      :limit="1"
      accept="image/jpeg,image/png"
      :on-change="choose"
      :on-remove="clearFile"
      ><el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">拖入照片，或<em>点击上传</em></div>
      <template #tip
        ><p class="muted">
          单人正面照 · JPEG / PNG · 不超过 5 MB。原图不会持久化保存。
        </p></template
      ></el-upload
    ><img
      v-if="preview"
      :src="preview"
      class="enroll-preview"
      alt="待录入人脸预览"
    /><template #footer
      ><el-button
        v-if="selected?.enrolled"
        type="danger"
        plain
        :disabled="saving"
        @click="removeFace"
        >清除人脸</el-button
      ><el-button @click="faceDialog = false">取消</el-button
      ><el-button
        type="primary"
        :loading="saving"
        :disabled="!file"
        @click="saveFace"
        >提取并保存特征</el-button
      ></template
    ></el-dialog
  >
</template>
