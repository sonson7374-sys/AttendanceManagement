-- V018: leave_requests.employee_number -> users.employee_number FK 제약 추가

ALTER TABLE leave_requests
    ADD CONSTRAINT leave_requests_employee_number_fkey
    FOREIGN KEY (employee_number) REFERENCES users(employee_number);
