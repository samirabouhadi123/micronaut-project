//package example.com;
//import io.micronaut.http.annotation.*;
//import jakarta.inject.Inject;
//
//@Controller
//public class ContactController {
//    private final ContactRepository contactRepository;
//
//
//    @Inject
//    public ContactController(ContactRepository contactRepository) {
//        this.contactRepository = contactRepository;
//    }
//
//    @Post("/create")
//    public ContactEntity create(@Body ContactEntity contact) {
//        return contactRepository.save(contact);
//    }
//
//    @Get("/getContacts")
//    public Iterable<ContactEntity> getContacts() {
//        return contactRepository.findAll();
//    }
//
//    @Delete("/delete/{id}")
//    public void delete(@PathVariable Long id) {
//        contactRepository.deleteById(id);
//    }
//}
