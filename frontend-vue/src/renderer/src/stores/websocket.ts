/**
 * stores/websocket.ts — WebSocket connection & event dispatch.
 *
 * Connects to the backend WS endpoint and dispatches events
 * to the appropriate Pinia stores.
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'
import { WsClient } from '@/api/ws'
import type { WsMessage, ExamParseQuestion, AiTaskResponse } from '@/api/types'
import { useQuestionStore } from './question'
import { useExamParseStore } from './examParse'
import { useNotificationStore } from './notification'

export const useWebSocketStore = defineStore('websocket', () => {
  // ── State ──

  const connected = ref(false)
  let client: WsClient | null = null

  // ── Actions ──

  /**
   * Connect to WebSocket with the given URL.
   * URL pattern: ws://{host}/ws/questions?user={username}&token={token}
   */
  function connect(wsBaseUrl: string, username: string, token: string): void {
    const url = `${wsBaseUrl}/ws/questions?user=${encodeURIComponent(username)}&token=${encodeURIComponent(token)}`

    if (client) {
      client.updateUrl(url)
      return
    }

    client = new WsClient({
      url,
      onMessage: handleMessage,
      onStatusChange(status) {
        connected.value = status
      },
      reconnectDelay: 3000
    })
    client.connect()
  }

  /** Disconnect and destroy the WS client. */
  function disconnect(): void {
    if (client) {
      client.destroy()
      client = null
    }
    connected.value = false
  }

  /** Central message dispatcher. */
  function handleMessage(msg: WsMessage): void {
    const { event, payload } = msg

    // OCR events
    if (event.startsWith('ocr.')) {
      handleOcrEvent(event, payload)
      return
    }

    // AI events
    if (event.startsWith('ai.')) {
      handleAiEvent(event, payload)
      return
    }

    // Exam parse events
    if (event.startsWith('exam.parse.')) {
      handleExamParseEvent(event, payload)
      return
    }
  }

  function handleOcrEvent(
    event: string,
    payload: Record<string, unknown>
  ): void {
    const store = useQuestionStore()
    const taskUuid = payload.taskUuid as string

    switch (event) {
      case 'ocr.task.processing':
        store.updateOcrTask(taskUuid, 'PROCESSING')
        break
      case 'ocr.task.succeeded':
        store.updateOcrTask(
          taskUuid,
          'SUCCESS',
          payload.recognizedText as string
        )
        break
      case 'ocr.task.failed':
        store.updateOcrTask(
          taskUuid,
          'FAILED',
          undefined,
          payload.errorMessage as string
        )
        break
    }
  }

  function handleAiEvent(
    event: string,
    payload: Record<string, unknown>
  ): void {
    const store = useQuestionStore()

    // Determine context by checking which view has a pending AI task
    const result: AiTaskResponse = {
      taskUuid: payload.taskUuid as string,
      questionUuid: payload.questionUuid as string,
      status: event === 'ai.analysis.succeeded' ? 'SUCCESS' : 'FAILED',
      suggestedTags: (payload.suggestedTags as string[]) ?? null,
      suggestedDifficulty: (payload.suggestedDifficulty as number) ?? null,
      reasoning: (payload.reasoning as string) ?? null,
      errorMessage: (payload.errorMessage as string) ?? null,
      appliedAt: null,
      createdAt: new Date().toISOString()
    }

    const questionUuid = result.questionUuid
    // Check if it's an entry AI task or bank AI task
    if (store.entryAi.pending.has(questionUuid)) {
      store.handleAiResult(result, 'entry')
    } else if (store.bankAi.pending.has(questionUuid)) {
      store.handleAiResult(result, 'bank')
    }
  }

  function handleExamParseEvent(
    event: string,
    payload: Record<string, unknown>
  ): void {
    const store = useExamParseStore()

    switch (event) {
      case 'exam.parse.question.result': {
        const taskUuid = payload.taskUuid as string
        const question = payload.question as ExamParseQuestion
        if (taskUuid && question) {
          store.handleWsQuestionResult(taskUuid, question)
        }
        break
      }
      case 'exam.parse.progress': {
        const taskUuid = payload.taskUuid as string
        const progress = payload.progress as number
        if (taskUuid && progress !== undefined) {
          store.handleWsProgress(taskUuid, progress)
        }
        break
      }
      case 'exam.parse.completed': {
        const taskUuid = payload.taskUuid as string
        const status = payload.status as string
        const questionCount = payload.questionCount as number | undefined
        const errorMsg = payload.errorMsg as string | undefined
        store.handleWsTaskCompleted(taskUuid, status, questionCount, errorMsg)
        break
      }
    }
  }

  function $reset(): void {
    disconnect()
  }

  return {
    connected,
    connect,
    disconnect,
    $reset
  }
})
