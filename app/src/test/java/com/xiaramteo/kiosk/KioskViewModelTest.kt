package com.xiaramteo.kiosk

import com.xiaramteo.kiosk.data.ItemResult
import com.xiaramteo.kiosk.data.KioskError
import com.xiaramteo.kiosk.data.MockLibrarySystem
import com.xiaramteo.kiosk.ui.IdleTimeout
import com.xiaramteo.kiosk.ui.KioskMode
import com.xiaramteo.kiosk.ui.KioskUiState
import com.xiaramteo.kiosk.ui.KioskViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KioskViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    /** 테스트에서는 가상 시간이 곧바로 흘러 자동 복귀 타이머가 터지므로 꺼 둔다. */
    private fun viewModel() = KioskViewModel(
        library = MockLibrarySystem(latencyMillis = 0),
        idleTimeout = IdleTimeout(scanSeconds = null, finishedSeconds = null),
    )

    @Test
    fun `대출 흐름 - 회원 인증 후 책을 모아 대출한다`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.startBorrow()
        vm.onBarcode("1000000001")
        advanceUntilIdle()

        val scanState = vm.state.value as KioskUiState.ItemScan
        assertEquals(KioskMode.BORROW, scanState.mode)
        assertEquals("김하늘", scanState.member?.name)

        vm.onBarcode("9788936434120")
        advanceUntilIdle()
        assertEquals(1, (vm.state.value as KioskUiState.ItemScan).scanned.size)

        vm.submit()
        advanceUntilIdle()

        val finished = vm.state.value as KioskUiState.Finished
        assertEquals(1, finished.succeeded.size)
        assertTrue(finished.results.single() is ItemResult.Borrowed)
    }

    @Test
    fun `같은 책을 두 번 찍으면 중복으로 알려준다`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.startReturn()
        vm.onBarcode("9788937473135")
        advanceUntilIdle()
        vm.onBarcode("9788937473135")
        advanceUntilIdle()

        val state = vm.state.value as KioskUiState.ItemScan
        assertEquals(1, state.scanned.size)
        assertEquals(KioskError.DUPLICATE_SCAN, state.error)
    }

    @Test
    fun `등록되지 않은 회원증은 오류를 남기고 회원 인증 화면에 머문다`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.startBorrow()
        vm.onBarcode("9999999999")
        advanceUntilIdle()

        val state = vm.state.value as KioskUiState.MemberScan
        assertEquals(KioskError.UNKNOWN_MEMBER, state.error)
    }

    @Test
    fun `대출 정지 회원은 책 스캔으로 넘어가지 못한다`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.startBorrow()
        vm.onBarcode("1000000003")
        advanceUntilIdle()

        val state = vm.state.value as KioskUiState.MemberScan
        assertEquals(KioskError.MEMBER_SUSPENDED, state.error)
    }

    @Test
    fun `대출 한도를 넘긴 회원은 실패로 기록된다`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.startBorrow()
        vm.onBarcode("1000000002")
        advanceUntilIdle()
        vm.onBarcode("9788936434120")
        advanceUntilIdle()
        vm.submit()
        advanceUntilIdle()

        val finished = vm.state.value as KioskUiState.Finished
        assertEquals(KioskError.LIMIT_EXCEEDED, finished.failed.single().error)
    }

    @Test
    fun `대출 중이 아닌 책을 반납하면 실패로 기록된다`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.startReturn()
        vm.onBarcode("9788936434120")
        advanceUntilIdle()
        vm.submit()
        advanceUntilIdle()

        val finished = vm.state.value as KioskUiState.Finished
        assertEquals(KioskError.NOT_LOANED, finished.failed.single().error)
    }

    @Test
    fun `무입력이 이어지면 처음 화면으로 돌아간다`() = runTest(dispatcher) {
        val vm = KioskViewModel(
            library = MockLibrarySystem(latencyMillis = 0),
            idleTimeout = IdleTimeout(scanSeconds = 5, finishedSeconds = null),
        )
        vm.startReturn()
        advanceTimeBy(4_000)
        assertTrue(vm.state.value is KioskUiState.ItemScan)

        advanceTimeBy(2_000)
        assertEquals(KioskUiState.Home, vm.state.value)
    }

    @Test
    fun `실제 자이아람터 장서 라벨도 조회된다`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.startBorrow()
        vm.onBarcode("1000000001")
        advanceUntilIdle()
        vm.onBarcode("EM0000000263")
        advanceUntilIdle()

        val state = vm.state.value as KioskUiState.ItemScan
        assertEquals("어두워지면 일어나라", state.scanned.single().title)
    }

    @Test
    fun `목록에서 책을 뺄 수 있다`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.startReturn()
        vm.onBarcode("9788937473135")
        advanceUntilIdle()

        val item = (vm.state.value as KioskUiState.ItemScan).scanned.single()
        vm.removeScanned(item)
        assertTrue((vm.state.value as KioskUiState.ItemScan).scanned.isEmpty())
    }
}
