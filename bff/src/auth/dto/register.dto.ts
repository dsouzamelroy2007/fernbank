import { IsEmail, IsNotEmpty, IsString, MinLength } from 'class-validator';

/** Thin shape validation only — the backend's own Bean Validation is the real authority. */
export class RegisterDto {
  @IsNotEmpty()
  @IsString()
  fullName!: string;

  @IsEmail()
  email!: string;

  @IsString()
  @MinLength(8)
  password!: string;
}
