#!/usr/bin/env python3
"""깨끗한 시드 사본에 내장 위반 E1~E5 를 적용한다. 사용: apply.py <seed-copy-dir>

각 변경은 원문 조각을 정확히 한 번 찾아 바꾼다(못 찾으면 실패). 깨끗한 시드가 바뀌면 여기서 멈추므로 조용히 어긋나지 않는다.
위반 목록과 의도는 experiment/README.md "침식 시드". 코드에는 설명 주석을 넣지 않는다(실제 프로젝트의 침식처럼 보이게).
  E1 서비스에 비즈니스 규칙: Book.loanTo 제거(getStatus/setStatus 만), LoanBookService 가 상태 비교·변경, Loan.start 공개
  E2 포트가 JPA 타입 반환: LoadBookPort.findRaw(UUID) → BookJpaEntity, LoanBookService 가 존재 확인에 사용
  E3 도메인에 @Entity: Member 에 @Entity/@Id/@Column, MemberJpaRepository extends JpaRepository<Member, UUID>
  E4 최상위 common 패키지: common.DateUtils.addDays, Loan 이 기한 계산에 사용
  E5 인바운드→아웃바운드 직접: BookController GET /books/{id} 가 BookJpaRepository 를 주입받아 BookJpaEntity 반환
"""
import sys
from pathlib import Path

root = Path(sys.argv[1]) / "src/main/java/com/example/lending"


def edit(rel: str, old: str, new: str) -> None:
    p = root / rel
    s = p.read_text()
    if s.count(old) != 1:
        sys.exit(f"apply.py: {rel}: expected exactly one match for {old[:60]!r}, found {s.count(old)}")
    p.write_text(s.replace(old, new))


def write(rel: str, text: str) -> None:
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    if p.exists():
        sys.exit(f"apply.py: {rel} already exists")
    p.write_text(text)


# ---- E1
edit("domain/Book.java", """    public Loan loanTo(UUID memberId, LocalDate loanedAt) {
        if (status == BookStatus.ON_LOAN) {
            throw new BookAlreadyOnLoanException(id);
        }
        Loan loan = Loan.start(id, memberId, loanedAt);
        status = BookStatus.ON_LOAN;
        return loan;
    }

""", "")
edit("domain/Book.java", """    public BookStatus getStatus() {
        return status;
    }
""", """    public BookStatus getStatus() {
        return status;
    }

    public void setStatus(BookStatus status) {
        this.status = status;
    }
""")
edit("domain/Book.java", "import java.time.LocalDate;\n", "")
edit("domain/Loan.java", """    // 대출 생성은 Book.loanTo를 통해서만 (대출 가능 여부 규칙을 우회하지 않도록 package-private)
    static Loan start(""", """    public static Loan start(""")
edit("application/service/LoanBookService.java", """        Loan loan = book.loanTo(command.memberId(), LocalDate.now());
""", """        if (book.getStatus() == BookStatus.ON_LOAN) {
            throw new BookAlreadyOnLoanException(book.getId());
        }
        Loan loan = Loan.start(book.getId(), command.memberId(), LocalDate.now());
        book.setStatus(BookStatus.ON_LOAN);
""")
edit("application/service/LoanBookService.java", """import com.example.lending.domain.Book;
""", """import com.example.lending.adapter.out.persistence.BookJpaEntity;
import com.example.lending.domain.Book;
import com.example.lending.domain.BookAlreadyOnLoanException;
""")
edit("application/service/LoanBookService.java", """import com.example.lending.domain.Loan;
""", """import com.example.lending.domain.BookStatus;
import com.example.lending.domain.Loan;
""")

# ---- E2
edit("application/port/out/LoadBookPort.java", """import com.example.lending.domain.Book;
""", """import com.example.lending.adapter.out.persistence.BookJpaEntity;
import com.example.lending.domain.Book;
""")
edit("application/port/out/LoadBookPort.java", """    Optional<Book> loadBook(UUID bookId);
""", """    Optional<Book> loadBook(UUID bookId);

    BookJpaEntity findRaw(UUID id);
""")
edit("adapter/out/persistence/BookPersistenceAdapter.java", """    @Override
    public void saveBook(Book book) {""", """    @Override
    public BookJpaEntity findRaw(UUID id) {
        return bookJpaRepository.findById(id).orElse(null);
    }

    @Override
    public void saveBook(Book book) {""")
edit("application/service/LoanBookService.java", """        Book book = loadBookPort.loadBook(command.bookId())""", """        BookJpaEntity raw = loadBookPort.findRaw(command.bookId());
        if (raw == null) {
            throw new BookNotFoundException(command.bookId());
        }
        Book book = loadBookPort.loadBook(command.bookId())""")

# ---- E3
edit("domain/Member.java", """import java.util.Objects;
""", """import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.util.Objects;
""")
edit("domain/Member.java", """public class Member {

    private final UUID id;
    private final String name;
""", """@Entity
public class Member {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    protected Member() {
    }
""")
write("adapter/out/persistence/MemberJpaRepository.java", """package com.example.lending.adapter.out.persistence;

import com.example.lending.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MemberJpaRepository extends JpaRepository<Member, UUID> {
}
""")

# ---- E4
write("common/DateUtils.java", """package com.example.lending.common;

import java.time.LocalDate;

public final class DateUtils {

    private DateUtils() {
    }

    public static LocalDate addDays(LocalDate date, int days) {
        return date.plusDays(days);
    }
}
""")
edit("domain/Loan.java", """import java.time.LocalDate;
""", """import com.example.lending.common.DateUtils;

import java.time.LocalDate;
""")
edit("domain/Loan.java", "loanedAt.plusDays(LOAN_PERIOD_DAYS)", "DateUtils.addDays(loanedAt, LOAN_PERIOD_DAYS)")

# ---- E5
edit("adapter/in/web/BookController.java", """import com.example.lending.application.port.in.RegisterBookUseCase;
""", """import com.example.lending.adapter.out.persistence.BookJpaEntity;
import com.example.lending.adapter.out.persistence.BookJpaRepository;
import com.example.lending.application.port.in.RegisterBookUseCase;
""")
edit("adapter/in/web/BookController.java", """import com.example.lending.domain.Book;
""", """import com.example.lending.domain.Book;
import com.example.lending.domain.BookNotFoundException;
""")
edit("adapter/in/web/BookController.java", """import org.springframework.http.HttpStatus;
""", """import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
""")
edit("adapter/in/web/BookController.java", """    private final RegisterBookUseCase registerBookUseCase;

    public BookController(RegisterBookUseCase registerBookUseCase) {
        this.registerBookUseCase = registerBookUseCase;
    }
""", """    private final RegisterBookUseCase registerBookUseCase;
    private final BookJpaRepository bookJpaRepository;

    public BookController(RegisterBookUseCase registerBookUseCase, BookJpaRepository bookJpaRepository) {
        this.registerBookUseCase = registerBookUseCase;
        this.bookJpaRepository = bookJpaRepository;
    }

    @GetMapping("/{id}")
    public BookJpaEntity getBook(@PathVariable UUID id) {
        return bookJpaRepository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
    }
""")
edit("adapter/in/web/BookController.java", """import org.springframework.web.bind.annotation.RestController;
""", """import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
""")
print("apply.py: E1-E5 applied")
