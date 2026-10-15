package com.example.lending.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 책 한 권의 예약 대기열(아직 소진되지 않은 예약들). 줄 순서, 맨 앞 예약자만 대출, 대출 시 소진 규칙을 가진다.
 */
public class ReservationQueue {

    private final UUID bookId;
    private final List<Reservation> waiting;

    private ReservationQueue(UUID bookId, List<Reservation> waiting) {
        this.bookId = Objects.requireNonNull(bookId, "bookId");
        this.waiting = new ArrayList<>(waiting);
        this.waiting.sort(Comparator.comparingLong(Reservation::getSequence));
    }

    public static ReservationQueue of(UUID bookId, List<Reservation> waiting) {
        if (waiting.stream().anyMatch(r -> r.isFulfilled() || !r.getBookId().equals(bookId))) {
            throw new IllegalArgumentException("queue holds only waiting reservations of book " + bookId);
        }
        return new ReservationQueue(bookId, waiting);
    }

    /**
     * 대출 중인 책에만 줄을 설 수 있다. 같은 회원은 한 번만.
     */
    public Reservation reserve(Book book, UUID memberId) {
        if (!book.getId().equals(bookId)) {
            throw new IllegalArgumentException("book does not match queue");
        }
        book.ensureReservable();
        if (waiting.stream().anyMatch(r -> r.getMemberId().equals(memberId))) {
            throw new AlreadyReservedException(bookId, memberId);
        }
        long next = waiting.isEmpty() ? 1 : waiting.get(waiting.size() - 1).getSequence() + 1;
        Reservation reservation = Reservation.place(bookId, memberId, next);
        waiting.add(reservation);
        return reservation;
    }

    /**
     * 대출하려는 회원을 들여보낸다. 줄이 없으면 누구나, 있으면 맨 앞 예약자만 — 그 예약은 소진되어 돌려준다.
     */
    public Optional<Reservation> admit(UUID memberId) {
        if (waiting.isEmpty()) {
            return Optional.empty();
        }
        Reservation head = waiting.get(0);
        if (!head.getMemberId().equals(memberId)) {
            throw new ReservedForOtherException(bookId, memberId);
        }
        head.fulfil();
        waiting.remove(0);
        return Optional.of(head);
    }

    /**
     * 1부터 시작하는 줄 위치.
     */
    public int positionOf(Reservation reservation) {
        int index = waiting.indexOf(reservation);
        if (index < 0) {
            throw new IllegalArgumentException("reservation is not waiting: " + reservation.getId());
        }
        return index + 1;
    }

    public List<Reservation> waiting() {
        return List.copyOf(waiting);
    }
}
