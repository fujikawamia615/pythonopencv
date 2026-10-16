<script setup>
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  DataAnalysis,
  User,
  OfficeBuilding,
  Key,
  Picture,
  List,
  SwitchButton,
  Lock,
} from "@element-plus/icons-vue";
import { api, clearSession } from "./api";
const route = useRoute(),
  router = useRouter();
const username = computed(() => {
  route.path;
  return sessionStorage.getItem("facegate-user") || "管理员";
});
const navigation = [
  ["/dashboard", "系统总览", DataAnalysis],
  ["/persons", "人员管理", User],
  ["/doors", "门禁管理", OfficeBuilding],
  ["/grants", "通行授权", Key],
  ["/recognition", "识别演示", Picture],
  ["/logs", "通行记录", List],
];
async function logout() {
  try {
    await api("/auth/logout", { method: "POST" });
  } catch {
  } finally {
    clearSession();
    router.replace("/login");
  }
}
</script>

<template>
  <router-view v-if="route.path === '/login'" />
  <div v-else class="shell">
    <aside class="sidebar">
      <router-link to="/dashboard" class="brand"
        ><span class="brand-icon"
          ><el-icon :size="25"><Lock /></el-icon></span
        ><span>FaceGate<small>人脸识别门禁系统</small></span></router-link
      >
      <div class="nav-label">工作空间</div>
      <el-menu
        :default-active="route.path"
        router
        background-color="transparent"
        text-color="#b7c5d9"
        active-text-color="#fff"
      >
        <el-menu-item
          v-for="[path, label, icon] in navigation"
          :key="path"
          :index="path"
          ><el-icon><component :is="icon" /></el-icon
          ><span>{{ label }}</span></el-menu-item
        >
      </el-menu>
      <div class="sidebar-bottom">
        <span class="status-dot"></span> 毕设演示环境<small
          >图片识别 · 模拟门禁</small
        >
      </div>
    </aside>
    <div class="workspace">
      <header class="topbar">
        <span
          >门禁管理控制台
          <el-tag size="small" effect="plain">DEMO</el-tag></span
        >
        <div class="account">
          <span class="avatar">{{ username.slice(0, 1).toUpperCase() }}</span
          >{{ username
          }}<el-button text :icon="SwitchButton" @click="logout"
            >退出</el-button
          >
        </div>
      </header>
      <main class="main-content">
        <div class="page-heading">
          <div>
            <div class="eyebrow">FACE ACCESS CONTROL</div>
            <h1>{{ route.meta.title }}</h1>
            <p>{{ route.meta.subtitle }}</p>
          </div>
          <el-tag type="info" effect="plain">上传图片模式</el-tag>
        </div>
        <router-view />
      </main>
    </div>
  </div>
</template>
