import { createRouter, createWebHistory } from "vue-router";
import { getToken } from "./api";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/login",
      component: () => import("./views/Login.vue"),
      meta: { title: "管理员登录" },
    },
    { path: "/", redirect: "/dashboard" },
    {
      path: "/dashboard",
      component: () => import("./views/Dashboard.vue"),
      meta: { title: "总览", subtitle: "门禁运行与通行数据，一目了然。" },
    },
    {
      path: "/persons",
      component: () => import("./views/Persons.vue"),
      meta: {
        title: "人员管理",
        subtitle: "维护人员信息，录入和更新人脸特征。",
      },
    },
    {
      path: "/doors",
      component: () => import("./views/Doors.vue"),
      meta: { title: "门禁管理", subtitle: "配置演示门禁及其启用状态。" },
    },
    {
      path: "/grants",
      component: () => import("./views/Grants.vue"),
      meta: {
        title: "通行授权",
        subtitle: "按人员、门禁和有效期管理通行权限。",
      },
    },
    {
      path: "/recognition",
      component: () => import("./views/Recognition.vue"),
      meta: {
        title: "识别演示",
        subtitle: "上传单人照片，完成识别、鉴权与模拟开门。",
      },
    },
    {
      path: "/logs",
      component: () => import("./views/Logs.vue"),
      meta: { title: "通行记录", subtitle: "每次通行判断都有据可查。" },
    },
    { path: "/:pathMatch(.*)*", redirect: "/dashboard" },
  ],
});
router.beforeEach((to) => {
  document.title = `${to.meta.title || "FaceGate"} · 人脸识别门禁系统`;
  if (to.path !== "/login" && !getToken()) return "/login";
  if (to.path === "/login" && getToken()) return "/dashboard";
});
window.addEventListener("session-expired", () => router.replace("/login"));
export default router;
