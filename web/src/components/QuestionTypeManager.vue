<template>
  <div class="modal-overlay" @click.self="$emit('close')">
    <div class="modal-panel">
      <div class="modal-header">
        <h3>题型管理</h3>
        <button class="close-btn" @click="$emit('close')">×</button>
      </div>

      <div class="modal-body">
        <!-- Existing types list -->
        <div class="type-list">
          <div
            v-for="t in questionTypeStore.types"
            :key="t.id"
            class="type-row"
            :class="{ editing: editingId === t.id }"
          >
            <template v-if="editingId === t.id">
              <input v-model="editForm.typeCode" class="type-input code-input" placeholder="编码" :disabled="t.system" />
              <input v-model="editForm.typeLabel" class="type-input label-input" placeholder="显示名称" />
              <input v-model.number="editForm.sortOrder" class="type-input sort-input" type="number" placeholder="排序" />
              <div class="row-actions">
                <button class="action-btn save" @click="saveEdit(t)">✓</button>
                <button class="action-btn cancel" @click="cancelEdit">✕</button>
              </div>
            </template>
            <template v-else>
              <span class="type-code">{{ t.typeCode }}</span>
              <span class="type-label">{{ t.typeLabel }}</span>
              <span class="type-sort">{{ t.sortOrder }}</span>
              <span v-if="t.system" class="system-badge">系统</span>
              <div class="row-actions">
                <button class="action-btn edit" @click="startEdit(t)" :disabled="t.system">✎</button>
                <button class="action-btn delete" @click="handleDelete(t)" :disabled="t.system">×</button>
              </div>
            </template>
          </div>
        </div>

        <!-- Add new type form -->
        <div class="add-form">
          <input v-model="newForm.typeCode" class="type-input code-input" placeholder="编码（如 FILL_BLANK）" />
          <input v-model="newForm.typeLabel" class="type-input label-input" placeholder="显示名称（如 填空题）" />
          <input v-model.number="newForm.sortOrder" class="type-input sort-input" type="number" placeholder="排序" />
          <button class="add-btn" :disabled="!newForm.typeCode || !newForm.typeLabel || adding" @click="handleAdd">
            {{ adding ? '…' : '+ 添加' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useQuestionTypeStore } from '@/stores/questionType'
import type { QuestionTypeResponse } from '@/api/types'

defineEmits<{ close: [] }>()

const questionTypeStore = useQuestionTypeStore()

const adding = ref(false)
const editingId = ref<number | null>(null)

const newForm = reactive({ typeCode: '', typeLabel: '', sortOrder: 100 })
const editForm = reactive({ typeCode: '', typeLabel: '', sortOrder: 0 })

function startEdit(t: QuestionTypeResponse) {
  editingId.value = t.id
  editForm.typeCode = t.typeCode
  editForm.typeLabel = t.typeLabel
  editForm.sortOrder = t.sortOrder
}

function cancelEdit() {
  editingId.value = null
}

async function saveEdit(t: QuestionTypeResponse) {
  await questionTypeStore.updateCustomType(t.id, {
    typeCode: editForm.typeCode,
    typeLabel: editForm.typeLabel,
    sortOrder: editForm.sortOrder
  })
  editingId.value = null
}

async function handleDelete(t: QuestionTypeResponse) {
  if (t.system) return
  await questionTypeStore.deleteCustomType(t.id)
}

async function handleAdd() {
  if (!newForm.typeCode || !newForm.typeLabel) return
  adding.value = true
  try {
    await questionTypeStore.createCustomType({
      typeCode: newForm.typeCode,
      typeLabel: newForm.typeLabel,
      sortOrder: newForm.sortOrder
    })
    newForm.typeCode = ''
    newForm.typeLabel = ''
    newForm.sortOrder = 100
  } finally {
    adding.value = false
  }
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: grid;
  place-items: center;
  z-index: 1000;
}

.modal-panel {
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  width: 540px;
  max-width: 90vw;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border-light);
}
.modal-header h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0;
}
.close-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  font-size: 20px;
  color: var(--color-text-muted);
  cursor: pointer;
  border-radius: var(--radius-sm);
  display: grid;
  place-items: center;
}
.close-btn:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.modal-body {
  padding: 16px 20px;
  overflow-y: auto;
  flex: 1;
}

.type-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 16px;
}

.type-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  transition: background var(--transition-fast);
}
.type-row:hover {
  background: var(--color-bg-hover);
}
.type-row.editing {
  background: var(--color-accent-muted);
}

.type-code {
  width: 140px;
  font-size: 12px;
  font-family: var(--font-mono);
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.type-label {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.type-sort {
  width: 40px;
  text-align: center;
  font-size: 12px;
  color: var(--color-text-muted);
}

.system-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: var(--radius-pill);
  background: var(--color-info-bg);
  color: var(--color-info);
  flex-shrink: 0;
}

.row-actions {
  display: flex;
  gap: 4px;
  margin-left: auto;
  flex-shrink: 0;
}
.action-btn {
  width: 26px;
  height: 26px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: transparent;
  cursor: pointer;
  display: grid;
  place-items: center;
  font-size: 13px;
  color: var(--color-text-secondary);
  transition: all var(--transition-fast);
}
.action-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}
.action-btn.save:hover { border-color: var(--color-success); color: var(--color-success); }
.action-btn.cancel:hover { border-color: var(--color-text-muted); color: var(--color-text-primary); }
.action-btn.edit:hover:not(:disabled) { border-color: var(--color-accent); color: var(--color-accent); }
.action-btn.delete:hover:not(:disabled) { border-color: var(--color-danger); color: var(--color-danger); }

.type-input {
  padding: 5px 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 12px;
  background: var(--color-bg-input);
  color: var(--color-text-primary);
}
.type-input:focus {
  outline: none;
  border-color: var(--color-accent);
}
.code-input { width: 130px; font-family: var(--font-mono); }
.label-input { flex: 1; }
.sort-input { width: 56px; text-align: center; }

.add-form {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 10px;
  border-top: 1px solid var(--color-border-light);
}
.add-btn {
  padding: 6px 14px;
  border: none;
  border-radius: var(--radius-sm);
  background: linear-gradient(135deg, var(--color-accent), #a29bfe);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: all var(--transition-fast);
}
.add-btn:hover:not(:disabled) {
  box-shadow: 0 4px 12px var(--color-accent-glow);
}
.add-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
