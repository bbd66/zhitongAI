import { registerPlugin } from '@capacitor/core';

export const NativeTTS = registerPlugin('NativeTTS', {
  web: {
    speak: async (options) => {
      return { success: false, error: '原生TTS仅在移动端可用' };
    },
    stop: async () => {
      return { success: true };
    },
    isSupported: async () => {
      return { value: false };
    }
  }
});

export default NativeTTS;
