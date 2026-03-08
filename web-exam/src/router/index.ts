import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
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
        { path: '', redirect: '/bank' },
        {
          path: 'bank',
          name: 'bank',
          component: () => import('@/views/BankView.vue')
        },
        {
          path: 'exams',
          name: 'exams',
          component: () => import('@/views/ExamListView.vue')
        },
        {
          path: 'compose/:id?',
          name: 'compose',
          component: () => import('@/views/ExamComposeView.vue')
        },
        {
          path: 'preview/:id',
          name: 'preview',
          component: () => import('@/views/ExamPreviewView.vue')
        }
      ]
    }
  ]
})

export default router
