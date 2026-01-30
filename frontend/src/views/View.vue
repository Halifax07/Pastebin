<template>
  <div class="view-container">
    <!-- Header -->
    <div class="header">
      <div class="header-left">
        <div class="logo-wrapper" @click="goHome" style="cursor: pointer;">
          <span class="logo-icon">🔐</span>
          <h1 class="logo">Secure Pastebin</h1>
        </div>
        <div class="tags-wrapper">
          <el-tag v-if="pasteData" class="custom-tag lang-tag">
            <el-icon><Document /></el-icon>
            {{ getLanguageLabel(pasteData.syntax) }}
          </el-tag>
          <el-tag v-if="pasteData?.isBurnAfterReading" class="custom-tag burn-tag">
            🔥 阅后即焚
          </el-tag>
        </div>
      </div>
      <div class="header-right">
        <el-button @click="handleAISummary" :loading="aiLoading" class="action-btn ai-btn">
          <span>✨ AI 总结</span>
        </el-button>
        <el-button @click="openRawView" class="action-btn" :disabled="pasteData?.isBurnAfterReading">
          <el-icon><Document /></el-icon>
          <span>Raw</span>
        </el-button>
        <el-button @click="handleDownload" class="action-btn" :disabled="pasteData?.isBurnAfterReading">
          <el-icon><Download /></el-icon>
          <span>下载</span>
        </el-button>
        <el-button @click="copyToClipboard" class="action-btn">
          <el-icon><CopyDocument /></el-icon>
          <span>复制代码</span>
        </el-button>
        <el-button @click="goHome" type="primary" class="action-btn create-btn">
          <el-icon><Plus /></el-icon>
          <span>创建新的</span>
        </el-button>
      </div>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="loading-container">
      <div class="loading-spinner"></div>
      <p>正在加载内容...</p>
    </div>

    <!-- Password Dialog -->
    <el-dialog
      v-model="showPasswordDialog"
      title="🔐 需要密码"
      width="420px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
      class="custom-dialog"
    >
      <div class="dialog-content">
        <p class="dialog-desc">此内容已加密，请输入密码以查看</p>
        <el-input
          v-model="password"
          placeholder="请输入访问密码"
          type="password"
          show-password
          size="large"
          @keyup.enter="handlePasswordSubmit"
        />
      </div>
      <template #footer>
        <el-button @click="goHome" size="large">取消</el-button>
        <el-button type="primary" @click="handlePasswordSubmit" size="large">
          解密查看
        </el-button>
      </template>
    </el-dialog>

    <!-- Burn Warning Dialog -->
    <el-dialog
      v-model="showBurnWarning"
      title="⚠️ 阅后即焚警告"
      width="500px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
      class="custom-dialog"
    >
      <div class="burn-warning-content">
        <div class="warning-icon">🔥</div>
        <p class="warning-text">此内容设置为"阅后即焚"</p>
        <p class="warning-desc">一旦查看将立即销毁且无法恢复！</p>
      </div>
      <template #footer>
        <el-button @click="goHome" size="large">取消</el-button>
        <el-button type="danger" @click="confirmBurn" size="large">
          我知道了，继续查看
        </el-button>
      </template>
    </el-dialog>

    <!-- AI Summary Card -->
    <div v-if="aiSummary" class="ai-summary-container">
      <div class="ai-summary-card">
        <div class="summary-header">
          <span class="summary-title">✨ AI 智能总结</span>
          <el-button text @click="aiSummary = ''" class="close-btn">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
        <div class="summary-content">
          {{ aiSummary }}
        </div>
      </div>
    </div>

    <!-- Editor (Read-only) -->
    <div v-if="pasteData && !loading && displayContent" class="editor-wrapper">
      <div class="editor-container">
        <vue-monaco-editor
          v-model:value="displayContent"
          :language="pasteData.syntax"
          :options="readOnlyEditorOptions"
          theme="vs-dark"
        />
      </div>
    </div>

    <!-- Error State -->
    <div v-if="error" class="error-container">
      <div class="error-content">
        <div class="error-icon">😢</div>
        <h2>内容不可用</h2>
        <p>{{ error }}</p>
        <el-button type="primary" @click="goHome" size="large" class="back-btn">
          返回首页
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CopyDocument, Loading, Close, MagicStick, Document, Plus, Download } from '@element-plus/icons-vue'
import { LANGUAGE_OPTIONS } from '@/utils/constants'
import { decrypt, isEncrypted } from '@/utils/crypto'
import { getPaste, getRawUrl, getDownloadUrl } from '@/api/paste'
import { summarizeContent } from '@/api/ai'
import type { PasteData } from '@/types/paste'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const error = ref('')
const pasteData = ref<PasteData | null>(null)
const decryptedContent = ref('')
const showPasswordDialog = ref(false)
const showBurnWarning = ref(false)
const password = ref('')
const needsPassword = ref(false)
const aiLoading = ref(false)
const aiSummary = ref('')

const readOnlyEditorOptions = {
  fontSize: 14,
  minimap: { enabled: true },
  automaticLayout: true,
  scrollBeyondLastLine: false,
  readOnly: true,
  cursorStyle: 'line',
  renderLineHighlight: 'none',
  wordWrap: 'on',
  padding: { top: 16, bottom: 16 },
  smoothScrolling: true,
}

// 计算显示的内容（解密后或原始内容）
const displayContent = computed(() => {
  if (needsPassword.value && !decryptedContent.value) {
    return ''
  }
  return decryptedContent.value || pasteData.value?.content || ''
})

const getLanguageLabel = (value: string) => {
  return LANGUAGE_OPTIONS.find(lang => lang.value === value)?.label || value
}

const goHome = () => {
  router.push('/')
}

/**
 * 打开 Raw 纯文本视图
 */
const openRawView = () => {
  const key = route.params.key as string
  if (!key) return
  
  if (pasteData.value?.isBurnAfterReading) {
    ElMessage.warning('阅后即焚内容不支持 Raw 模式')
    return
  }
  
  // 如果内容是加密的，Raw 模式也会显示加密后的内容
  if (needsPassword.value) {
    ElMessage.warning('加密内容的 Raw 模式会显示密文')
  }
  
  window.open(getRawUrl(key), '_blank')
}

/**
 * 下载文件
 */
const handleDownload = () => {
  const key = route.params.key as string
  if (!key) return
  
  if (pasteData.value?.isBurnAfterReading) {
    ElMessage.warning('阅后即焚内容不支持下载')
    return
  }
  
  // 如果内容是加密的，需要在前端解密后下载
  if (needsPassword.value && decryptedContent.value) {
    // 前端生成解密后的文件下载
    downloadDecryptedContent()
    return
  }
  
  if (needsPassword.value && !decryptedContent.value) {
    ElMessage.warning('请先输入密码解密内容')
    return
  }
  
  // 非加密内容直接从后端下载
  window.open(getDownloadUrl(key), '_blank')
}

/**
 * 下载解密后的内容（前端生成）
 */
const downloadDecryptedContent = () => {
  const content = displayContent.value
  if (!content) {
    ElMessage.warning('没有可下载的内容')
    return
  }
  
  const key = route.params.key as string
  const syntax = pasteData.value?.syntax || 'plaintext'
  const extension = getFileExtension(syntax)
  const filename = `${key}${extension}`
  
  // 创建 Blob 并下载
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  
  ElMessage.success('文件下载成功')
}

/**
 * 根据语法类型获取文件扩展名
 */
const getFileExtension = (syntax: string): string => {
  const extensionMap: Record<string, string> = {
    'java': '.java',
    'python': '.py',
    'javascript': '.js',
    'typescript': '.ts',
    'cpp': '.cpp',
    'csharp': '.cs',
    'go': '.go',
    'rust': '.rs',
    'php': '.php',
    'ruby': '.rb',
    'html': '.html',
    'css': '.css',
    'json': '.json',
    'xml': '.xml',
    'markdown': '.md',
    'sql': '.sql',
    'shell': '.sh',
    'yaml': '.yml',
  }
  return extensionMap[syntax?.toLowerCase()] || '.txt'
}

const copyToClipboard = async () => {
  const content = displayContent.value
  if (!content) {
    ElMessage.warning('没有可复制的内容')
    return
  }
  
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success('已复制到剪贴板')
  } catch (err) {
    ElMessage.error('复制失败')
  }
}

const handleAISummary = async () => {
  const content = displayContent.value
  if (!content) {
    ElMessage.warning('没有可总结的内容')
    return
  }

  if (aiLoading.value) {
    return
  }

  aiLoading.value = true
  aiSummary.value = ''

  try {
    const response = await summarizeContent({ content })
    aiSummary.value = response.summary
    ElMessage.success(`总结完成（使用 ${response.tokens} tokens）`)
  } catch (error: any) {
    console.error('AI 总结失败:', error)
    ElMessage.error(error.response?.data?.summary || 'AI 总结失败，请重试')
  } finally {
    aiLoading.value = false
  }
}

const handlePasswordSubmit = () => {
  if (!password.value.trim()) {
    ElMessage.warning('请输入密码')
    return
  }

  try {
    if (!pasteData.value) {
      ElMessage.error('数据加载失败')
      return
    }

    // 尝试解密
    const decrypted = decrypt(pasteData.value.content, password.value)
    decryptedContent.value = decrypted
    needsPassword.value = false
    showPasswordDialog.value = false
    
    ElMessage.success('解密成功！')
  } catch (error: any) {
    ElMessage.error(error.message || '密码错误，请重试')
    password.value = ''
  }
}

const confirmBurn = async () => {
  showBurnWarning.value = false
  
  // 阅后即焚确认后，重新加载数据（此时会触发后端删除）
  await loadPasteData(true)
}

const loadPasteData = async (skipBurnWarning: boolean = false) => {
  const key = route.params.id as string
  
  if (!key) {
    error.value = '无效的链接'
    loading.value = false
    return
  }

  loading.value = true
  
  try {
    const data = await getPaste(key)
    pasteData.value = data

    // 检查是否加密
    if (isEncrypted(data.content)) {
      needsPassword.value = true
      showPasswordDialog.value = true
      loading.value = false
      return
    }

    // 如果不加密，直接显示
    decryptedContent.value = data.content
    loading.value = false
    
  } catch (err: any) {
    console.error('加载失败:', err)
    error.value = err.response?.data?.message || err.message || '内容不存在或已过期'
    loading.value = false
  }
}

onMounted(async () => {
  const key = route.params.id as string
  
  if (!key) {
    error.value = '无效的链接'
    loading.value = false
    return
  }

  // 首次加载时，先获取数据判断是否阅后即焚
  try {
    const data = await getPaste(key)
    
    // 如果是阅后即焚，先显示警告
    if (data.isBurnAfterReading) {
      pasteData.value = data
      showBurnWarning.value = true
      loading.value = false
      return
    }

    // 不是阅后即焚，直接加载
    pasteData.value = data

    // 检查是否加密
    if (isEncrypted(data.content)) {
      needsPassword.value = true
      showPasswordDialog.value = true
      loading.value = false
      return
    }

    // 不加密，直接显示
    decryptedContent.value = data.content
    loading.value = false
    
  } catch (err: any) {
    console.error('加载失败:', err)
    error.value = err.response?.data?.message || err.message || '内容不存在或已过期'
    loading.value = false
  }
})
</script>

<style scoped>
.view-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #0d1117; /* GitHub Dark Dimmed */
  color: #c9d1d9;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 24px;
  background-color: #161b22;
  border-bottom: 1px solid #30363d;
  height: 60px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.logo-wrapper {
  display: flex;
  align-items: center;
  gap: 10px;
  transition: transform 0.2s ease;
}

.logo-wrapper:hover {
  transform: scale(1.02);
}

.logo-icon {
  font-size: 24px;
}

.logo {
  font-size: 20px;
  font-weight: 600;
  color: #c9d1d9;
  margin: 0;
  letter-spacing: 0.5px;
}

.tags-wrapper {
  display: flex;
  gap: 8px;
  padding-left: 16px;
  border-left: 1px solid #30363d;
}

.custom-tag {
  background: #21262d;
  border: 1px solid #30363d;
  color: #8b949e;
  padding: 4px 12px;
  border-radius: 20px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.lang-tag {
  color: #58a6ff;
  border-color: rgba(56, 139, 253, 0.4);
}

.burn-tag {
  color: #f85149;
  border-color: rgba(248, 81, 73, 0.4);
}

.header-right {
  display: flex;
  gap: 10px;
}

.action-btn {
  background-color: #21262d;
  border: 1px solid #30363d;
  color: #c9d1d9;
  padding: 8px 16px;
  border-radius: 6px;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.action-btn:hover {
  background-color: #30363d;
  border-color: #8b949e;
}

.ai-btn {
  background-color: rgba(187, 128, 9, 0.1);
  border-color: rgba(187, 128, 9, 0.4);
  color: #e3b341;
}

.ai-btn:hover {
  background-color: rgba(187, 128, 9, 0.2);
}

.create-btn {
  background-color: #238636;
  border: 1px solid rgba(240, 246, 252, 0.1);
  color: #ffffff;
}

.create-btn:hover {
  background-color: #2ea043;
}

.editor-wrapper {
  flex: 1;
  padding: 0;
  overflow: hidden;
  position: relative;
}

.editor-container {
  height: 100%;
  width: 100%;
}

.loading-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: #8b949e;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #30363d;
  border-top-color: #58a6ff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-container p {
  margin-top: 16px;
  font-size: 14px;
}

.error-container {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
}

.error-content {
  text-align: center;
  color: #c9d1d9;
  background: #161b22;
  padding: 40px;
  border-radius: 12px;
  border: 1px solid #30363d;
}

.error-icon {
  font-size: 48px;
  margin-bottom: 20px;
}

.error-content h2 {
  font-size: 24px;
  margin-bottom: 12px;
  color: #ff7b72;
}

.error-content p {
  font-size: 16px;
  color: #8b949e;
  margin-bottom: 30px;
}

.back-btn {
  background-color: #238636;
  border: none;
  padding: 10px 24px;
}

.ai-summary-container {
  margin: 16px 24px 0;
  animation: slideDown 0.3s ease;
  z-index: 100;
  position: absolute;
  top: 60px; /* Header Height */
  right: 24px;
  width: 400px;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.ai-summary-card {
  background: #161b22;
  border: 1px solid #e3b341;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
  overflow: hidden;
}

.summary-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: rgba(187, 128, 9, 0.1);
  border-bottom: 1px solid rgba(187, 128, 9, 0.2);
}

.summary-title {
  font-size: 14px;
  font-weight: 600;
  color: #e3b341;
  display: flex;
  align-items: center;
  gap: 8px;
}

.close-btn {
  color: #8b949e;
  padding: 4px;
  height: auto;
}

.close-btn:hover {
  color: #c9d1d9;
  background-color: rgba(255, 255, 255, 0.1);
}

.summary-content {
  padding: 16px;
  color: #c9d1d9;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 400px;
  overflow-y: auto;
}

/* Dialog Styles */
.custom-dialog {
  /* controlled by el-dialog deep styles mostly */
}

.dialog-content {
  padding: 10px 0;
}

.dialog-desc {
  color: #8b949e;
  margin-bottom: 20px;
  text-align: center;
  font-size: 14px;
}

.burn-warning-content {
  text-align: center;
  padding: 20px 0;
}

.warning-icon {
  font-size: 48px;
  margin-bottom: 16px;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
}

.warning-text {
  font-size: 18px;
  font-weight: 600;
  color: #ff7b72;
  margin-bottom: 8px;
}

.warning-desc {
  color: #8b949e;
  font-size: 14px;
}

:deep(.el-dialog) {
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 8px;
}

:deep(.el-dialog__header) {
  padding: 16px 20px;
  border-bottom: 1px solid #30363d;
  margin-right: 0;
}

:deep(.el-dialog__title) {
  color: #c9d1d9;
  font-size: 16px;
}

:deep(.el-dialog__body) {
  padding: 20px;
}

:deep(.el-dialog__footer) {
  padding: 16px 20px;
  border-top: 1px solid #30363d;
}

:deep(.el-input__wrapper) {
  background-color: #0d1117;
  box-shadow: 0 0 0 1px #30363d inset;
}

:deep(.el-input__inner) {
  color: #c9d1d9;
}
</style>
