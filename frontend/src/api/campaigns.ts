export type CreateCampaignRequest = {
    name: string
    subject: string
    message: string
}

export type CampaignResponse = {
    id: number
    name: string
    status: string
}

function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null
}

function readError(data: unknown, status: number): string {
    if (isObject(data)) {
        const messages: string[] = []

        if (isObject(data.errors)) {
            for (const value of Object.values(data.errors)) {
                if (Array.isArray(value)) {
                    for (const item of value) {
                        if (typeof item === 'string') {
                            messages.push(item)
                        }
                    }
                }
            }
        }

        if (messages.length > 0) {
            return messages.join(' ')
        }

        if (typeof data.message === 'string') {
            return data.message
        }
    }

    return `Request failed (${status}). Check that the backend is running.`
}

export async function createCampaign(
    request: CreateCampaignRequest,
): Promise<CampaignResponse> {
    let response: Response

    try {
        response = await fetch('/api/v1/campaigns', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(request),
        })
    } catch {
        throw new Error(
            'Could not confirm whether the campaign was saved. Check the backend before trying again.',
        )
    }

    let data: unknown

    try {
        data = await response.json()
    } catch {
        throw new Error(
            'The server returned an unreadable response. Check the backend before trying again.',
        )
    }

    if (!response.ok) {
        throw new Error(readError(data, response.status))
    }

    if (
        !isObject(data) ||
        typeof data.id !== 'number' ||
        !Number.isSafeInteger(data.id) ||
        data.id <= 0 ||
        typeof data.name !== 'string' ||
        typeof data.status !== 'string'
    ) {
        throw new Error(
            'The save response was unexpected. Check the backend before trying again.',
        )
    }

    return {
        id: data.id,
        name: data.name,
        status: data.status,
    }
}