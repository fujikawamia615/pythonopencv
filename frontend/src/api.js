import { ElMessage } from "element-plus";

export const getToken = () => sessionStorage.getItem("facegate-token");
export function clearSession() {
  sessionStorage.removeItem("facegate-token");
  sessionStorage.removeItem("facegate-user");
}

export async function api(path, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 70000);
  try {
    const headers = { ...options.headers };
    const token = getToken();
    if (token) headers.Authorization = `Bearer ${token}`;
    if (options.body && !(options.body instanceof FormData)) {
      headers["Content-Type"] = "application/json";
      options = { ...options, body: JSON.stringify(options.body) };
    }
    const response = await fetch(`/api${path}`, {
      ...options,
      headers,
      signal: controller.signal,
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      if (response.status === 401 && path !== "/auth/login") {
        clearSession();
        window.dispatchEvent(new Event("session-expired"));
      }
      throw new Error(body.message || `请求失败 (${response.status})`);
    }
    return body;
  } catch (error) {
    const message =
      error.name === "AbortError"
        ? "请求超时，请检查后端和识别服务"
        : error.message;
    ElMessage.error(message || "网络连接失败");
    throw error;
  } finally {
    clearTimeout(timer);
  }
}

export function imageForm(file) {
  const data = new FormData();
  data.append("file", file);
  return data;
}
export const formatTime = (value) =>
  value ? value.replace("T", " ").slice(0, 19) : "—";
export const scoreText = (value) =>
  value == null ? "—" : Number(value).toFixed(3);
export const reasons = {
  ALLOWED: "允许通行",
  UNKNOWN_FACE: "陌生人",
  AMBIGUOUS_FACE: "匹配不明确",
  PERSON_DISABLED: "人员禁用",
  DOOR_DISABLED: "门禁停用",
  NO_PERMISSION: "无有效权限",
  COOLDOWN: "重复通行",
  INVALID_IMAGE: "无效人脸图片",
  FACE_UNAVAILABLE: "识别服务异常",
};
