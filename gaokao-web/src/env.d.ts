/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_GAOKAO_MOCK?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module 'katex/dist/contrib/auto-render.mjs' {
  const renderMathInElement: (element: HTMLElement, options?: Record<string, unknown>) => void
  export default renderMathInElement
}