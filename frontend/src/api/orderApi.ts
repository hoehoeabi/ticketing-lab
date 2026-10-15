import { apiClient } from './client';
import type { RsData } from '../types';

export const orderApi = {
  createOrder: async (ticketId: number): Promise<RsData<number>> => {
    const response = await apiClient.post<RsData<number>>(`/orders/${ticketId}`);
    return response.data;
  },
};
