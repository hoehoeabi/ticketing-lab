import { apiClient } from './client';
import type { RsData, LoginResponseDto, EmailSendResponseDto } from '../types';

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

  sendEmailCode: async (email: string): Promise<RsData<EmailSendResponseDto>> => {
    const response = await apiClient.post<RsData<EmailSendResponseDto>>('/auth/email/send', {
      email,
    });
    return response.data;
  },

  verifyEmailCode: async (email: string, code: string): Promise<RsData<null>> => {
    const response = await apiClient.post<RsData<null>>('/auth/email/verify', {
      email,
      code,
    });
    return response.data;
  },

  getMyProfile: async (): Promise<RsData<{ id: number; email: string }>> => {
    const response = await apiClient.get<RsData<{ id: number; email: string }>>('/users/me');
    return response.data;
  },
};
