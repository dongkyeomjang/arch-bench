package com.example.lending.application.service;

import com.example.lending.application.port.in.RegisterBookUseCase;
import com.example.lending.application.port.out.SaveBookPort;
import com.example.lending.domain.Book;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RegisterBookService implements RegisterBookUseCase {

    private final SaveBookPort saveBookPort;

    public RegisterBookService(SaveBookPort saveBookPort) {
        this.saveBookPort = saveBookPort;
    }

    @Override
    public Book registerBook(RegisterBookCommand command) {
        Book book = Book.register(command.title());
        saveBookPort.saveBook(book);
        return book;
    }
}
