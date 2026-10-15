package com.example.lending.adapter.in.web;

import com.example.lending.application.port.in.DiscardBookUseCase;
import com.example.lending.application.port.in.RegisterBookUseCase;
import com.example.lending.application.port.in.RegisterBookUseCase.RegisterBookCommand;
import com.example.lending.application.port.in.SearchBooksUseCase;
import com.example.lending.domain.Book;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/books")
public class BookController {

    private final RegisterBookUseCase registerBookUseCase;
    private final SearchBooksUseCase searchBooksUseCase;
    private final DiscardBookUseCase discardBookUseCase;

    public BookController(RegisterBookUseCase registerBookUseCase, SearchBooksUseCase searchBooksUseCase,
                          DiscardBookUseCase discardBookUseCase) {
        this.registerBookUseCase = registerBookUseCase;
        this.searchBooksUseCase = searchBooksUseCase;
        this.discardBookUseCase = discardBookUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookResponse registerBook(@RequestBody RegisterBookRequest request) {
        Book book = registerBookUseCase.registerBook(new RegisterBookCommand(request.title()));
        return BookResponse.from(book);
    }

    @GetMapping
    public List<BookResponse> searchBooks(@RequestParam(required = false) String title) {
        return searchBooksUseCase.searchBooks(title).stream().map(BookResponse::from).toList();
    }

    @PostMapping("/{bookId}/discard")
    public BookResponse discardBook(@PathVariable UUID bookId) {
        return BookResponse.from(discardBookUseCase.discardBook(bookId));
    }
}
