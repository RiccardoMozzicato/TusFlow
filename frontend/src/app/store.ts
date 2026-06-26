import { configureStore } from '@reduxjs/toolkit'
import authReducer from '@/features/auth/authSlice'
import chatReducer from '@/features/chat/chatSlice'
import documentsReducer from '@/features/documents/documentsSlice'
import documentDetailsReducer from '@/features/documents/documentDetailsSlice'
import healthReducer from '@/features/health/healthSlice'
import uiReducer from '@/features/ui/uiSlice'

export const store = configureStore({
  reducer: {
    auth: authReducer,
    chat: chatReducer,
    documents: documentsReducer,
    documentDetails: documentDetailsReducer,
    health: healthReducer,
    ui: uiReducer,
    // feature slices get registered here as the app grows
  },
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch
