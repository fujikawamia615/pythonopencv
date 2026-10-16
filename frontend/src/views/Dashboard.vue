<script setup>
import { onMounted, ref, computed } from "vue";
import { useRouter } from "vue-router";
import {
  Refresh,
  Right,
  User,
  CircleCheck,
  Unlock,
  CircleClose,
} from "@element-plus/icons-vue";
import { api, formatTime, reasons } from "../api";
const data = ref(null),
  busy = ref(false),
  router = useRouter();
const max = computed(() =>
  Math.max(
    1,
    ...(data.value?.trend || []).map((d) => Math.max(d.allowed, d.denied)),
  ),
);
const cards = computed(() => [
  {
    label: "人员总数",
    value: data.value?.persons ?? "—",
    note: `已录入人脸 ${data.value?.enrolled ?? 0} 人`,
    icon: User,
    color: "blue",
  },
  {
    label: "今日允许",
    value: data.value?.todayAllowed ?? "—",
    note: "通过身份与权限校验",
    icon: CircleCheck,
    color: "green",
  },
  {
    label: "今日拒绝",
    value: data.value?.todayDenied ?? "—",
    note: "包含重复通行与无效图片",
    icon: CircleClose,
    color: "orange",
  },
  {
    label: "门禁设备",
    value: data.value?.doors ?? "—",
    note: "当前配置的模拟门禁",
    icon: Unlock,
    color: "purple",
  },
]);
async function load() {
  busy.value = true;
  try {
    data.value = await api("/dashboard");
  } catch {
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div v-loading="busy">
    <div class="welcome-banner">
      <div>
        <span class="eyebrow">ACCESS OVERVIEW</span>
        <h2>有序管理，安心通行</h2>
        <p>从一张照片开始，体验完整的人脸识别门禁流程。</p>
        <el-button
          type="primary"
          :icon="Right"
          @click="router.push('/recognition')"
          >开始识别演示</el-button
        >
      </div>
      <div class="banner-orbit">
        <el-icon :size="64"><Unlock /></el-icon><span>FACE<br />ACCESS</span>
      </div>
    </div>
    <div class="stats-grid">
      <el-card v-for="card in cards" :key="card.label" shadow="never"
        ><div class="stat-top">
          <span>{{ card.label }}</span
          ><span :class="['stat-icon', card.color]"
            ><el-icon :size="20"><component :is="card.icon" /></el-icon
          ></span>
        </div>
        <strong class="stat-value">{{ card.value }}</strong>
        <div class="muted stat-note">{{ card.note }}</div></el-card
      >
    </div>
    <div class="dashboard-grid">
      <el-card shadow="never"
        ><template #header
          ><div class="card-header">
            <h3>近 7 日通行趋势</h3>
            <div class="chart-legend">
              <span class="green-dot"></span>允许<span class="orange-dot"></span
              >拒绝
            </div>
          </div></template
        >
        <div class="trend-chart">
          <div
            v-for="day in data?.trend || []"
            :key="day.date"
            class="trend-column"
          >
            <div class="bar-area">
              <div
                class="bar green-bar"
                :style="{ height: `${(day.allowed / max) * 150 + 3}px` }"
                :title="`允许 ${day.allowed}`"
              >
                <span>{{ day.allowed }}</span>
              </div>
              <div
                class="bar orange-bar"
                :style="{ height: `${(day.denied / max) * 150 + 3}px` }"
                :title="`拒绝 ${day.denied}`"
              >
                <span>{{ day.denied }}</span>
              </div>
            </div>
            <small>{{ day.date.slice(5) }}</small>
          </div>
        </div></el-card
      >
      <el-card shadow="never"
        ><template #header><h3>服务与快速操作</h3></template>
        <div class="service-status">
          <span>Python 识别服务</span
          ><el-tag :type="data?.faceServiceReady ? 'success' : 'danger'">{{
            data?.faceServiceReady ? "模型已就绪" : "未就绪"
          }}</el-tag>
        </div>
        <p class="muted">
          业务时间：{{ formatTime(data?.serverTime) }}<br />时区：Asia/Shanghai
        </p>
        <el-divider />
        <div class="quick-links">
          <el-button @click="router.push('/persons')"
            >① 添加人员与录入人脸</el-button
          ><el-button @click="router.push('/grants')"
            >② 配置门禁通行授权</el-button
          ><el-button @click="router.push('/recognition')"
            >③ 上传照片并模拟通行</el-button
          >
        </div></el-card
      >
    </div>
    <el-card shadow="never" class="section-gap"
      ><template #header
        ><div class="card-header">
          <h3>最近通行记录</h3>
          <div>
            <el-button text :icon="Refresh" @click="load">刷新</el-button
            ><el-button text @click="router.push('/logs')"
              >查看全部 →</el-button
            >
          </div>
        </div></template
      ><el-table
        :data="data?.recent || []"
        empty-text="暂无记录，先去体验一次识别吧"
        ><el-table-column prop="personName" label="人员"
          ><template #default="{ row }">{{
            row.personName || "未识别人员"
          }}</template></el-table-column
        ><el-table-column prop="doorName" label="门禁" /><el-table-column
          label="结果"
          ><template #default="{ row }"
            ><el-tag
              :type="row.allowed ? 'success' : 'danger'"
              effect="light"
              >{{ reasons[row.reason] || row.reason }}</el-tag
            ></template
          ></el-table-column
        ><el-table-column label="时间" min-width="170"
          ><template #default="{ row }">{{
            formatTime(row.createdAt)
          }}</template></el-table-column
        ></el-table
      ></el-card
    >
  </div>
</template>
