import { createRouter, createWebHashHistory } from 'vue-router'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue')
    },
    {
      path: '/',
      component: () => import('@/views/AppShell.vue'),
      children: [
        { path: '', redirect: '/entry' },
        {
          path: 'entry',
          name: 'entry',
          component: () => import('@/views/EntryView.vue')
        },
        {
          path: 'bank',
          name: 'bank',
          component: () => import('@/views/BankView.vue')
        },
        {
          path: 'exam-parse',
          name: 'exam-parse',
          component: () => import('@/views/ExamParseView.vue')
        }
      ]
    }
  ]
})

export default router
