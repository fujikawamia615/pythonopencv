<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { Lock, User, Right } from "@element-plus/icons-vue";
import { api } from "../api";
const router = useRouter(),
  loading = ref(false),
  formRef = ref();
const form = reactive({ username: "admin", password: "" });
const rules = {
  username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }],
};
async function login() {
  if (!(await formRef.value.validate().catch(() => false))) return;
  loading.value = true;
  try {
    const result = await api("/auth/login", { method: "POST", body: form });
    sessionStorage.setItem("facegate-token", result.token);
    sessionStorage.setItem("facegate-user", result.username);
    router.replace("/dashboard");
  } catch {
  } finally {
    loading.value = false;
  }
}
</script>
<template>
  <div class="login-page">
    <div class="login-story">
      <div class="login-brand">
        <el-icon :size="30"><Lock /></el-icon> FaceGate
      </div>
      <div>
        <div class="eyebrow">SMART ACCESS · SIMPLE CONTROL</div>
        <h1>让每一次通行<br />都有迹可循。</h1>
        <p>从人脸录入到身份识别，<br />用一个控制台连接人员、权限与门禁。</p>
        <div class="login-pills">
          <span>Vue 3 + Element Plus</span><span>Spring Boot</span
          ><span>OpenCV</span>
        </div>
      </div>
      <small>人脸识别门禁系统 · 毕业设计演示版</small>
    </div>
    <div class="login-panel">
      <div class="login-card">
        <el-tag effect="plain">管理员入口</el-tag>
        <h2>欢迎回来</h2>
        <p class="muted">登录以管理你的门禁工作空间</p>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="login"
          ><el-form-item label="用户名" prop="username"
            ><el-input
              v-model="form.username"
              :prefix-icon="User"
              size="large"
              autocomplete="username"
              maxlength="50" /></el-form-item
          ><el-form-item label="密码" prop="password"
            ><el-input
              v-model="form.password"
              :prefix-icon="Lock"
              type="password"
              show-password
              size="large"
              autocomplete="current-password"
              maxlength="100"
              @keyup.enter="login" /></el-form-item
          ><el-button
            type="primary"
            size="large"
            :loading="loading"
            class="full-width"
            :icon="Right"
            @click="login"
            >登录控制台</el-button
          ></el-form
        >
        <p class="login-hint">使用部署时配置的管理员账号与密码。</p>
      </div>
    </div>
  </div>
</template>
