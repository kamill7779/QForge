import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

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
      component: () => import('@/components/AppShell.vue'),
      children: [
        { path: '', redirect: '/sessions' },
        {
          path: 'sessions',
          name: 'sessions',
          component: () => import('@/views/SessionListView.vue')
        },
        {
          path: 'sessions/:id',
          name: 'session-workbench',
          component: () => import('@/views/IngestWorkbenchView.vue')
        },
        {
          path: 'corpus',
          name: 'corpus',
          component: () => import('@/views/CorpusView.vue')
        },
        {
          path: 'photo-query',
          name: 'photo-query',
          component: () => import('@/views/PhotoQueryView.vue')
        }
      ]
    }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.path === '/login') return true
  if (auth.isLoggedIn) return true
  const saved = auth.loadSavedCredentials()
  if (saved) {
    auth.restoreSession(saved.username, saved.token)
    return true
  }
  return '/login'
})

export default router