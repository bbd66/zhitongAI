import { registerPlugin } from '@capacitor/core';

export const NativeASR = registerPlugin('NativeASR', {
  web: {
    initialize: async () => {
      return { success: false, error: '原生插件仅在移动端可用' };
    },
    isInitialized: async () => {
      return { value: false };
    },
    startListening: async () => {
      return { success: false };
    },
    stopListening: async () => {
      return { success: true };
    },
    recognize: async () => {
      return { text: '' };
    }
  }
});

export default NativeASR;
