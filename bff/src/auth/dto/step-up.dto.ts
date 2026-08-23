import { IsNotEmpty, IsString } from 'class-validator';

export class StepUpDto {
  @IsNotEmpty()
  @IsString()
  code!: string;
}
