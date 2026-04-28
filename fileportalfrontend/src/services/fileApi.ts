import { getCsrfToken } from '../utils';

const FILES_BASE_URL = '/api/v1/files';

// Sharing scope const object (instead of enum for TypeScript compatibility)
export const SharingScope = {
  PERSONAL: 'PERSONAL',
  DEPARTMENT: 'DEPARTMENT',
  ORGANIZATION: 'ORGANIZATION',
} as const;

export type SharingScope = typeof SharingScope[keyof typeof SharingScope];

// Permission level const object (instead of enum for TypeScript compatibility)
export const PermissionLevel = {
  READ_ONLY: 'READ_ONLY',
  READ_WRITE: 'READ_WRITE',
} as const;

export type PermissionLevel = typeof PermissionLevel[keyof typeof PermissionLevel];

// File share type
export interface FileShare {
  id?: number;
  fileID: string;
  userID: string;
  permission: PermissionLevel;
  timestamp: string;
}

// File metadata type - matches exactly what the backend sends
export interface FileMetadata {
  id: string;
  fileName: string;
  contentType?: string;
  size?: number;
  owner?: string; // UUID of the owner (kept for backward compatibility)
  ownerName?: string; // Human-readable name of the owner
  sharingScope?: SharingScope;
  createdAt: string;
  completedAt?: string;
  version?: number;
}


// Paginated response type
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

// Get files parameters type
export interface GetFilesParams {
  contentType?: string;
  dateFrom?: string;
  dateTo?: string;
  sort?: string;
  page?: number;
  size?: number;
  sharingScope?: SharingScope;
}

/**
 * Get files metadata with optional filters and pagination
 */
export const getFilesMetadata = async (
  params?: GetFilesParams
): Promise<PageResponse<FileMetadata>> => {
  const queryParams = new URLSearchParams();
  if (params?.contentType) queryParams.append('contentType', params.contentType);
  if (params?.dateFrom) queryParams.append('dateFrom', params.dateFrom);
  if (params?.dateTo) queryParams.append('dateTo', params.dateTo);
  if (params?.sort) queryParams.append('sort', params.sort);
  if (params?.page !== undefined) queryParams.append('page', params.page.toString());
  if (params?.size !== undefined) queryParams.append('size', params.size.toString());
  if (params?.sharingScope) queryParams.append('sharingScope', params.sharingScope);

  console.log(queryParams);
  const url = `${FILES_BASE_URL}${queryParams.toString() ? '?' + queryParams.toString() : ''}`;

  const response = await fetch(url, {
    method: 'GET',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    const errorText = await response.text();
    console.error('Get files metadata error:', errorText);
    throw new Error(`${response.status}: Failed to fetch files - ${response.statusText}`);
  }

  return response.json();
};

/**
 * Get all files from the repository without pagination
 */
export const getAllFiles = async (): Promise<FileMetadata[]> => {
  const url = `${FILES_BASE_URL}/all`;

  const response = await fetch(url, {
    method: 'GET',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    const errorText = await response.text();
    console.error('Get all files error:', errorText);
    throw new Error(`${response.status}: Failed to fetch all files - ${response.statusText}`);
  }

  return response.json();
};

/**
 * Get file metadata by ID
 */
export const getFileMetadataById = async (fileId: string): Promise<FileMetadata> => {
  const response = await fetch(`${FILES_BASE_URL}/${fileId}`, {
    method: 'GET',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    const errorText = await response.text();
    console.error('Get file metadata error:', errorText);
    throw new Error(`Failed to fetch file metadata: ${response.statusText}`);
  }

  return response.json();
};

/**
 * Delete a file by ID
 */
export const deleteFile = async (fileId: string): Promise<void> => {
  const csrfToken = getCsrfToken();
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
  };

  if (csrfToken) {
    headers['X-XSRF-TOKEN'] = csrfToken;
  }

  const response = await fetch(`${FILES_BASE_URL}/${fileId}`, {
    method: 'DELETE',
    credentials: 'include',
    headers,
  });

  if (!response.ok) {
    const errorText = await response.text();
    console.error('Delete file error:', errorText);
    throw new Error(`Failed to delete file: ${response.statusText}`);
  }
};

/**
 * Download the last version of a file by ID
 */
export const downloadFile = async (fileId: string): Promise<Blob> => {
  const response = await fetch(`${FILES_BASE_URL}/${fileId}/download`, {
    method: 'GET',
    credentials: 'include',
  });

  if (!response.ok) {
    const errorText = await response.text();
    console.error('Download file error:', errorText);
    throw new Error(`Failed to download file: ${response.statusText}`);
  }

  return response.blob();
};

/**
 * Get files shared with the current user
 */
export const getSharedFiles = async (
  page: number = 0,
  size: number = 10
): Promise<PageResponse<FileMetadata>> => {
  const queryParams = new URLSearchParams();
  queryParams.append('page', page.toString());
  queryParams.append('size', size.toString());

  const url = `${FILES_BASE_URL}/my-shared?${queryParams.toString()}`;

  const response = await fetch(url, {
    method: 'GET',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    const errorText = await response.text();
    console.error('Get shared files error:', errorText);
    throw new Error(`${response.status}: Failed to fetch shared files - ${response.statusText}`);
  }

  return response.json();
};

/**
 * Share a file with another user
 */
export const shareFile = async (
  fileId: string,
  userId: string,
  permission: PermissionLevel
): Promise<FileShare> => {
  const csrfToken = getCsrfToken();
  console.log('CSRF Token for share:', csrfToken);
  console.log('All cookies:', document.cookie);
  
  const headers: HeadersInit = {};

  if (csrfToken) {
    headers['X-XSRF-TOKEN'] = csrfToken;
    // Also try the Spring default header name
    headers['X-CSRF-TOKEN'] = csrfToken;
  } else {
    console.error('No CSRF token available!');
  }

  const queryParams = new URLSearchParams();
  queryParams.append('userId', userId);
  queryParams.append('permission', permission);

  const url = `${FILES_BASE_URL}/${fileId}/shares?${queryParams.toString()}`;
  console.log('Share URL:', url);
  console.log('Share Headers:', headers);

  const response = await fetch(url, {
    method: 'POST',
    credentials: 'include',
    headers,
  });

  if (!response.ok) {
    const errorText = await response.text();
    console.error('Share file error:', errorText);
    console.error('Response status:', response.status);
    console.error('Response headers:', response.headers);
    throw new Error(`Failed to share file: ${response.statusText}`);
  }

  return response.json();
};

/**
 * Get file shares for a specific file
 */
export const getFileShares = async (
  fileId: string,
  page: number = 0,
  size: number = 10
): Promise<PageResponse<FileShare>> => {
  const queryParams = new URLSearchParams();
  queryParams.append('page', page.toString());
  queryParams.append('size', size.toString());

  const url = `${FILES_BASE_URL}/${fileId}/shares?${queryParams.toString()}`;

  const response = await fetch(url, {
    method: 'GET',
    credentials: 'include',
  });

  if (!response.ok) {
    const errorText = await response.text();
    console.error('Get file shares error:', errorText);
    throw new Error(`Failed to fetch file shares: ${response.statusText}`);
  }

  return response.json();
};

/**
 * Delete a file share
 */
export const deleteFileShare = async (fileId: string, shareId: number): Promise<void> => {
  const csrfToken = getCsrfToken();
  const headers: HeadersInit = {};

  if (csrfToken) {
    headers['X-XSRF-TOKEN'] = csrfToken;
    headers['X-CSRF-TOKEN'] = csrfToken;
  }

  const response = await fetch(`${FILES_BASE_URL}/${fileId}/shares/${shareId}`, {
    method: 'DELETE',
    credentials: 'include',
    headers,
  });

  if (!response.ok) {
    const errorText = await response.text();
    console.error('Delete file share error:', errorText);
    throw new Error(`Failed to delete file share: ${response.statusText}`);
  }
};

/**
 * Update file metadata
 */
export const updateFileMetadata = async (
  fileId: string,
  version: number,
  metadata: Record<string, string>
): Promise<void> => {
  const csrfToken = getCsrfToken();
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
  };

  if (csrfToken) {
    headers['X-XSRF-TOKEN'] = csrfToken;
    headers['X-CSRF-TOKEN'] = csrfToken;
  }

  const response = await fetch(`${FILES_BASE_URL}/${fileId}/versions/${version}`, {
    method: 'PATCH',
    credentials: 'include',
    headers,
    body: JSON.stringify(metadata),
  });

  if (!response.ok) {
    const errorText = await response.text();
    console.error('Update file metadata error:', errorText);
    throw new Error(`Failed to update file metadata: ${response.statusText}`);
  }
};

/**
 * Create a new version of a file (returns upload URL for TUS)
 */
export const createFileVersion = async (fileId: string): Promise<string> => {
  const csrfToken = getCsrfToken();
  const headers: HeadersInit = {};

  if (csrfToken) {
    headers['X-XSRF-TOKEN'] = csrfToken;
    headers['X-CSRF-TOKEN'] = csrfToken;
  }

  const response = await fetch(`${FILES_BASE_URL}/${fileId}/versions`, {
    method: 'POST',
    credentials: 'include',
    headers,
    redirect: 'manual', // Don't follow redirects automatically
  });

  // The backend returns 307 with Location header
  if (response.status === 307 || response.type === 'opaqueredirect') {
    const locationHeader = response.headers.get('Location');
    if (locationHeader) {
      return locationHeader;
    }
    // If Location header is not available due to CORS, construct the URL
    return `/uploads?fileId=${fileId}`;
  }

  if (!response.ok) {
    const errorText = await response.text();
    console.error('Create file version error:', errorText);
    throw new Error(`Failed to create file version: ${response.statusText}`);
  }

  throw new Error('Unexpected response from server');
};

// File version type
export interface FileVersion {
  id: number;
  version: number;
  filename: string;
  size: number;
  contentType: string;
  checksum?: string;
  status: 'MERGE_PENDING' | 'AVAILABLE' | 'DELETED';
  metadata?: Record<string, string>;
  createdAt: string;
  lastUpdateAt: string;
}

/**
 * Get all versions of a file
 */
export const getFileVersions = async (fileId: string): Promise<FileVersion[]> => {
  const response = await fetch(`${FILES_BASE_URL}/${fileId}/versions`, {
    method: 'GET',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    const errorText = await response.text();
    console.error('Get file versions error:', errorText);
    throw new Error(`Failed to fetch file versions: ${response.statusText}`);
  }

  return response.json();
};

/**
 * Download a specific version of a file
 */
export const downloadFileVersion = async (fileId: string, version: number): Promise<Blob> => {
  const response = await fetch(`${FILES_BASE_URL}/${fileId}/download?version=${version}`, {
    method: 'GET',
    credentials: 'include',
  });

  if (!response.ok) {
    const errorText = await response.text();
    console.error('Download file version error:', errorText);
    throw new Error(`Failed to download file version: ${response.statusText}`);
  }

  return response.blob();
};

// AI-generated metadata type
export type SensitivityLevel = 'PUBLIC' | 'INTERNAL' | 'CONFIDENTIAL' | 'HIGHLY_CONFIDENTIAL';

export interface MetadataAIDTO {
  tags?: string | string[];
  summary?: string;
  sensitivity?: SensitivityLevel;
  category?: string;
  language?: string;
  keyEntities?: string | string[];
  topics?: string | string[];
  sentiment?: 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL';
  confidenceScore?: number;
}

export interface UpdateGeneratedMetadataRequest {
  summary?: string;
  tags?: string;
  sensitivity?: SensitivityLevel;
}

/**
 * Get AI-generated metadata for a file
 */
export const getFileGeneratedMetadata = async (
  fileId: string,
  version?: number
): Promise<MetadataAIDTO> => {
  const queryParams = version !== undefined ? `?version=${version}` : '';
  const response = await fetch(`${FILES_BASE_URL}/generatedmetadata/${fileId}${queryParams}`, {
    method: 'GET',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    const errorText = await response.text();
    console.error('Get AI metadata error:', errorText);
    throw new Error(`Failed to fetch AI-generated metadata: ${response.statusText}`);
  }

  return response.json();
};

/**
 * Update AI-generated metadata for a file
 */
export const updateGeneratedMetadata = async (
  fileId: string,
  metadata: UpdateGeneratedMetadataRequest,
  version?: number
): Promise<void> => {
  const csrfToken = getCsrfToken();
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
  };

  if (csrfToken) {
    headers['X-XSRF-TOKEN'] = csrfToken;
    headers['X-CSRF-TOKEN'] = csrfToken;
  }

  const queryParams = version !== undefined ? `?version=${version}` : '';
  const response = await fetch(`${FILES_BASE_URL}/generatedmetadata/${fileId}${queryParams}`, {
    method: 'PATCH',
    credentials: 'include',
    headers,
    body: JSON.stringify(metadata),
  });

  if (!response.ok) {
    const errorText = await response.text();
    console.error('Update AI metadata error:', errorText);
    throw new Error(`Failed to update AI-generated metadata: ${response.statusText}`);
  }
};
