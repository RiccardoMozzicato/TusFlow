import { getCsrfToken } from '../utils';
interface RagQueryRequest {
  query: string;
  topK?: number;
}
interface RagQueryResponse {
  query: string;
  answer: string;
}
const API_BASE_URL = '/api/v1/files';
export const chatService = {
  async query(query: string, topK: number = 5): Promise<RagQueryResponse> {
    const csrfToken = getCsrfToken();
    const response = await fetch(`${API_BASE_URL}/query`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(csrfToken && { 'X-XSRF-TOKEN': csrfToken }),
      },
      credentials: 'include',
      body: JSON.stringify({
        query,
        topK,
      } as RagQueryRequest),
    });
    if (!response.ok) {
      throw new Error(`Failed to query AI: ${response.statusText}`);
    }
    return response.json();
  },
};
