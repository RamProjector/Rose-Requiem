package com.roserequiem.app.ui.screens.downloads

import com.roserequiem.app.domain.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsSelectionActionsTest {
    @Test
    fun pausedSelectionOffersContinueAndRemove() {
        assertEquals(
            setOf(SelectionAction.CONTINUE, SelectionAction.REMOVE),
            resolveSelectionActions(setOf(DownloadStatus.PAUSED), batchActive = false),
        )
    }

    @Test
    fun failedSelectionOffersRetryAndRemove() {
        assertEquals(
            setOf(SelectionAction.RETRY, SelectionAction.REMOVE),
            resolveSelectionActions(setOf(DownloadStatus.FAILED), batchActive = false),
        )
    }

    @Test
    fun activeSelectionOffersCancelButNotRemove() {
        assertEquals(
            setOf(SelectionAction.CANCEL),
            resolveSelectionActions(setOf(DownloadStatus.DOWNLOADING), batchActive = false),
        )
    }

    @Test
    fun activeBatchSuppressesContinueAndRetry() {
        assertEquals(
            setOf(SelectionAction.REMOVE),
            resolveSelectionActions(
                setOf(DownloadStatus.PAUSED, DownloadStatus.FAILED),
                batchActive = true,
            ),
        )
    }

    @Test
    fun mixedSelectionOffersEveryApplicableAction() {
        assertEquals(
            setOf(
                SelectionAction.CONTINUE,
                SelectionAction.RETRY,
                SelectionAction.CANCEL,
                SelectionAction.REMOVE,
            ),
            resolveSelectionActions(
                setOf(
                    DownloadStatus.PAUSED,
                    DownloadStatus.FAILED,
                    DownloadStatus.QUEUED,
                    DownloadStatus.SUCCESS,
                ),
                batchActive = false,
            ),
        )
    }

    @Test
    fun emptySelectionOffersNoActions() {
        assertEquals(
            emptySet<SelectionAction>(),
            resolveSelectionActions(emptySet(), batchActive = false),
        )
    }
}
