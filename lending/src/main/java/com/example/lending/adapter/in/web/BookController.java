package com.example.lending.adapter.in.web;

import com.example.lending.application.port.in.RegisterBookUseCase;
import com.example.lending.application.port.in.RegisterBookUseCase.RegisterBookCommand;
import com.example.lending.domain.Book;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/books")
public class BookController {

    private final RegisterBookUseCase registerBookUseCase;

    public BookController(RegisterBookUseCase registerBookUseCase) {
        this.registerBookUseCase = registerBookUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookResponse registerBook(@RequestBody RegisterBookRequest request) {
        Book book = registerBookUseCase.registerBook(new RegisterBookCommand(request.title()));
        return BookResponse.from(book);
    }
}
