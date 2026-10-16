<script setup>
import { ref, onMounted, onBeforeUnmount } from "vue";
import { ElMessage } from "element-plus";
import {
  UploadFilled,
  Lock,
  Unlock,
  Picture,
  Refresh,
} from "@element-plus/icons-vue";
import { api, imageForm, scoreText } from "../api";
const doors = ref([]),
  doorId = ref(null),
  file = ref(null),
  preview = ref(""),
  result = ref(null),
  busy = ref(false),
  isOpen = ref(false),
  uploadRef = ref();
let timer;
function reset() {
  clearTimeout(timer);
  isOpen.value = false;
  result.value = null;
  file.value = null;
  if (preview.value) URL.revokeObjectURL(preview.value);
  preview.value = "";
}
function choose(upload) {
  reset();
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
async function recognize() {
  if (!doorId.value || !file.value)
    return ElMessage.warning("请选择门禁并上传照片");
  busy.value = true;
  result.value = null;
  isOpen.value = false;
  clearTimeout(timer);
  try {
    result.value = await api(`/access/recognize?doorId=${doorId.value}`, {
      method: "POST",
      body: imageForm(file.value),
    });
    isOpen.value = result.value.doorOpen;
    if (isOpen.value)
      timer = setTimeout(() => {
        isOpen.value = false;
      }, 5000);
  } catch {
  } finally {
    busy.value = false;
  }
}
onMounted(async () => {
  try {
    doors.value = await api("/doors");
    doorId.value = doors.value.find((d) => d.enabled)?.id || doors.value[0]?.id;
  } catch {}
});
onBeforeUnmount(reset);
</script>
<template>
  <div class="recognition-layout">
    <el-card shadow="never"
      ><template #header
        ><div class="card-header">
          <h3>上传通行照片</h3>
          <el-tag effect="plain">JPEG / PNG</el-tag>
        </div></template
      ><el-form label-position="top"
        ><el-form-item label="选择门禁"
          ><el-select
            v-model="doorId"
            class="full-width"
            :disabled="busy"
            @change="
              reset();
              uploadRef?.clearFiles();
            "
            ><el-option
              v-for="door in doors"
              :key="door.id"
              :label="`${door.name}${door.enabled ? '' : '（已停用）'}`"
              :value="door.id" /></el-select></el-form-item
      ></el-form>
      <el-upload
        ref="uploadRef"
        drag
        :auto-upload="false"
        :limit="1"
        :disabled="busy"
        accept="image/jpeg,image/png"
        :on-change="choose"
        :on-remove="reset"
        ><div class="scan-upload">
          <img v-if="preview" :src="preview" alt="待识别人脸预览" /><template
            v-else
            ><el-icon :size="46"><UploadFilled /></el-icon>
            <h3>将照片拖入这里</h3>
            <p>或点击选择一张单人正面照</p>
            <small>不超过 5 MB · 无需摄像头</small></template
          >
        </div></el-upload
      >
      <p class="muted">
        请先在人员管理中录入人脸，并配置该门禁的有效通行权限。
      </p>
      <el-button
        type="primary"
        size="large"
        :icon="Picture"
        :loading="busy"
        :disabled="!file || !doorId"
        class="full-width"
        @click="recognize"
        >{{ busy ? "正在识别与校验权限…" : "开始识别" }}</el-button
      ></el-card
    >
    <div>
      <el-card
        shadow="never"
        :class="['simulator', { 'simulator-open': isOpen }]"
        ><div class="eyebrow">VIRTUAL DOOR CONTROLLER</div>
        <div class="virtual-door">
          <el-icon :size="68"><Unlock v-if="isOpen" /><Lock v-else /></el-icon
          ><span class="door-led"></span>
        </div>
        <h2>{{ isOpen ? "门禁已开启" : "门禁已关闭" }}</h2>
        <p>
          {{ isOpen ? "允许通行，5 秒后自动关闭" : "等待有效身份与通行授权" }}
        </p>
        <el-tag :type="isOpen ? 'success' : 'info'"
          >{{ isOpen ? "OPEN" : "LOCKED" }} · 模拟设备</el-tag
        ></el-card
      >
      <el-card v-if="result" shadow="never" class="section-gap"
        ><el-result
          :icon="result.allowed ? 'success' : 'error'"
          :title="result.allowed ? '允许通行' : '拒绝通行'"
          :sub-title="result.message"
        /><el-descriptions :column="1" border
          ><el-descriptions-item label="识别人员">{{
            result.personName || "未识别"
          }}</el-descriptions-item
          ><el-descriptions-item label="余弦相似度"
            >{{ scoreText(result.score) }}
            <span class="muted">（并非准确率）</span></el-descriptions-item
          ><el-descriptions-item label="记录编号"
            >#{{ result.logId }}</el-descriptions-item
          ></el-descriptions
        ><el-button
          class="section-gap"
          :icon="Refresh"
          :disabled="busy"
          @click="
            reset();
            uploadRef?.clearFiles();
          "
          >更换照片</el-button
        ></el-card
      >
      <el-card v-else shadow="never" class="section-gap"
        ><h3>一次完整的通行判断</h3>
        <ol class="process-list">
          <li>检测人脸并提取特征</li>
          <li>与已录入人员进行相似度比对</li>
          <li>校验人员状态、门禁与有效期</li>
          <li>模拟开门，并保存通行记录</li>
        </ol></el-card
      >
    </div>
  </div>
</template>
