export type ImportResponse = {
    importId: number
    campaignId: number
    status: string
}

export type ImportStatus = ImportResponse & {
    originalFilename: string
    processedRows: number
    addedRows: number
    duplicateRows: number
    invalidRows: number
    lastError: string | null
}

export type ImportRowError = {
    recordNumber: number
    rawValue: string | null
    reason: string
}

export type ImportErrorPage = {
    importId: number
    errors: ImportRowError[]
    hasMore: boolean
    nextAfterRecord: number
}

async function readResponse<T>(response: Response): Promise<T> {
    const text = await response.text()
    let data: unknown

    try {
        data = JSON.parse(text)
    } catch {
        throw new Error(
            `Server returned an unreadable response (${response.status}).`,
        )
    }

    if (!response.ok) {
        let message = `Request failed (${response.status}).`

        if (
            typeof data === 'object' &&
            data !== null &&
            'message' in data &&
            typeof data.message === 'string'
        ) {
            message = data.message
        }

        throw new Error(message)
    }

    // Types follow the backend DTO contract.
    return data as T
}

export async function uploadRecipients(
    campaignId: number,
    file: File,
): Promise<ImportResponse> {
    const body = new FormData()
    body.append('file', file)

    // Browser sets the multipart Content-Type and boundary.
    const response = await fetch(
        `/api/v1/campaigns/${campaignId}/imports`,
        {
            method: 'POST',
            body,
        },
    )

    return readResponse<ImportResponse>(response)
}

export async function getImportStatus(
    campaignId: number,
    importId: number,
    signal: AbortSignal,
): Promise<ImportStatus> {
    const response = await fetch(
        `/api/v1/campaigns/${campaignId}/imports/${importId}`,
        { signal, cache: 'no-store' },
    )

    return readResponse<ImportStatus>(response)
}

export async function getImportErrors(
    campaignId: number,
    importId: number,
    afterRecord: number,
    signal: AbortSignal,
): Promise<ImportErrorPage> {
    const response = await fetch(
        `/api/v1/campaigns/${campaignId}/imports/${importId}/errors` +
        `?afterRecord=${afterRecord}&limit=50`,
        { signal, cache: 'no-store' },
    )

    return readResponse<ImportErrorPage>(response)
}