import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

import 'katex/dist/katex.min.css'
import renderMathInElement from 'katex/dist/contrib/auto-render.mjs'

import './styles/variables.css'
import './styles/base.css'

;(window as Window & { renderMathInElement?: typeof renderMathInElement }).renderMathInElement =
  renderMathInElement

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')