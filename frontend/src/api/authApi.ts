import { apiClient } from './client';
import type { RsData, LoginResponseDto } from '../types';

export const authApi = {
  login: async (email: string, password: string): Promise<RsData<LoginResponseDto>> => {
    const response = await apiClient.post<RsData<LoginResponseDto>>('/users/login', {
      email,
      password,
    });
    return response.data;
  },

  signup: async (email: string, password: string): Promise<RsData<unknown>> => {
    const response = await apiClient.post<RsData<unknown>>('/users/signup', {
      email,
      password,
    });
    return response.data;
  },

  getMyProfile: async (): Promise<RsData<{ id: number; email: string }>> => {
    const response = await apiClient.get<RsData<{ id: number; email: string }>>('/users/me');
    return response.data;
  },
};
