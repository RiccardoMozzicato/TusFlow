import * as tus from "tus-js-client";
import {getCsrfToken } from "../utils";

export const SharingScope = {
  PERSONAL: 'PERSONAL',
  DEPARTMENT: 'DEPARTMENT',
  ORGANIZATION: 'ORGANIZATION',
} as const;

export type SharingScope = typeof SharingScope[keyof typeof SharingScope];

export interface UploadController {
    promise: Promise<string>;
    pause: () => void;
    resume: () => void;
    abort: () => void;
}

function uploadFile(
    file: File, 
    fileId?: string, 
    sharingScope: SharingScope = SharingScope.PERSONAL,
    metadata?: Record<string, string>,
    onProgress?: (percentage: number) => void
): UploadController {
    let upload: tus.Upload;
    let isPausedIntentionally = false;
    let isAbortedIntentionally = false;

    const promise = new Promise<string>((resolve, reject) => {
        const xsrfToken = getCsrfToken();

        // Costruisci l'endpoint per la POST iniziale con i parametri fileId e sharingScope
        let endpoint = "http://localhost:8080/api/v1/uploads";
        const params = new URLSearchParams();
        
        if (fileId) {
            params.append('fileId', fileId);
        }
        params.append('sharingScope', sharingScope);
        
        endpoint = `${endpoint}?${params.toString()}`;

        // Crea l'upload
        upload = new tus.Upload(file, {
            endpoint: endpoint,
            retryDelays: [0, 3000, 5000, 10000, 20000],
            // Enable chunked upload: send file in chunks of 256KB each
            chunkSize: 256 * 1024, // 256 KB per chunk
            metadata: {
                filename: file.name,
                ...metadata
            },
            headers: xsrfToken ? { "X-XSRF-TOKEN": xsrfToken } : {},

            onBeforeRequest: function (req) {
                // Imposta Content-Type solo per la richiesta POST iniziale
                if (req.getMethod() === 'POST') {
                    req.setHeader('Content-Type', file.type);
                }
            },

            onError: function (error) {
                // Non considerare la pausa intenzionale come un errore
                if (isPausedIntentionally) {
                    console.log("Upload messo in pausa (non è un errore)");
                    return;
                }
                // Non considerare l'abort intenzionale come un errore (lo gestiamo separatamente)
                if (isAbortedIntentionally) {
                    console.log("Upload annullato intenzionalmente");
                    reject(new Error("Upload cancelled by user"));
                    return;
                }
                console.error("Upload fallito:", error);
                reject(error);
            },

            onProgress: function (bytesUploaded, bytesTotal) {
                const percentage = (bytesUploaded / bytesTotal) * 100;
                console.log(bytesUploaded, bytesTotal, percentage.toFixed(2) + "%");
                if (onProgress) {
                    onProgress(percentage);
                }
            },

            onSuccess: function () {
                console.log("Upload completato! URL:", upload.url);
                resolve(upload.url || '');
            }
        });

        upload.start();
    });

    return {
        promise,
        pause: () => {
            if (upload) {
                isPausedIntentionally = true;
                upload.abort();
                console.log("Upload in pausa");
            }
        },
        resume: () => {
            if (upload) {
                isPausedIntentionally = false;
                upload.start();
                console.log("Upload ripreso");
            }
        },
        abort: () => {
            if (upload) {
                isAbortedIntentionally = true;
                upload.abort(true);
                console.log("Upload annullato");
            }
        }
    };
}

export default uploadFile;