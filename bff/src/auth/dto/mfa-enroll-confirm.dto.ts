import { IsNotEmpty, IsString } from 'class-validator';

export class MfaEnrollConfirmDto {
  @IsNotEmpty()
  @IsString()
  code!: string;
}
