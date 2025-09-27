package com.example.taskmanagement_backend.dtos.UserDto;

import lombok.*;

import java.io.Serializable;
import java.util.List;

//@Data
//@NoArgsConstructor
//@AllArgsConstructor
//@Builder
//public class UserResponseDto {
//    private Integer id;
//    private String username;
//    private String email;
//    private String avt_url;
//    private Boolean deleted;
//    private String status;
//    private List<String> roleNames;
//    private String organizationName;
//
//
//}
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponseDto implements Serializable {

    private Long id;

    private String email;

    private String firstName;

    private String lastName;

    private String avt_url;

    private Boolean firstLogin;
    private Boolean deleted;

    private String status;

    private List<String> roleNames;

    private String organizationName;

}
