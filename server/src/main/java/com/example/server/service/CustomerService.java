package com.example.server.service;

import com.example.server.entity.Customer;
import com.example.server.respository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {
    @Autowired
    private CustomerRepository customerRepository;

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    public Optional<Customer> getCustomerById(Long id) {
        return customerRepository.findById(id);
    }

    public Customer createCustomer(Customer customer) {
        return customerRepository.save(customer);
    }

    public Customer updateCustomer(Long id, Customer customerDetails) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found"));
        customer.setCustomerName(customerDetails.getCustomerName());
        customer.setCustomerAddress(customerDetails.getCustomerAddress());
        customer.setCustomerPhone(customerDetails.getCustomerPhone());
        return customerRepository.save(customer);
    }

    public void deleteCustomer(Long id) {
        customerRepository.deleteById(id);
    }

    public List<Customer> searchCustomersByNameOrPhone(String keyword) {
        return customerRepository.findByCustomerNameOrPhoneContaining(keyword);
    }
}