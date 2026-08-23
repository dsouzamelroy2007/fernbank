import { IsNotEmpty, IsString } from 'class-validator';

export class MfaVerifyDto {
  @IsNotEmpty()
  @IsString()
  mfaToken!: string;

  @IsNotEmpty()
  @IsString()
  code!: string;
}
